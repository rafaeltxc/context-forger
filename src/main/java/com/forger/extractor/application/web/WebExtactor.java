package com.forger.extractor.application.web;

import com.forger.extractor.data.cache.ExtractionCaching;
import com.forger.extractor.domain.model.Extraction;
import com.forger.extractor.domain.record.CrawlerConfiguration;
import com.forger.extractor.exception.UnreachableUriException;
import com.forger.extractor.exception.UriConnectException;
import com.forger.extractor.exception.WebContentExtractionException;
import com.forger.extractor.exception.WebUriExtractionException;
import com.forger.extractor.infrastructure.CrawlerConfigurationProvider;
import com.forger.extractor.service.ExtractionService;
import com.forger.extractor.utils.UriUtils;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Web page scraper class, with the objective gathering all the needed data for the context
 * forging.
 * <p>
 * The web page scraping is made with Mutiny asynchronous code, accelerating the page
 * content scrape. A synchronous code may be run subscribing Mutiny with only one instance.
 */
@ApplicationScoped
public class WebExtactor {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 11.0; " +
            "Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36";

    private static final String CONNECTION_REFERER = "http://www.google.com";

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(3);

    private static final Duration MIN_BACKOFF = Duration.ofSeconds(10);

    private static final Integer MAX_RETRIES = 3;

    private final ExtractionService extractionService;

    private final ExtractionCaching extractionCaching;

    private final UriUtils uriUtils;

    private final CrawlerConfigurationProvider configurationProvider;

    @Inject
    public WebExtactor(
            ExtractionService extractionService,
            ExtractionCaching extractionCaching,
            CrawlerConfigurationProvider configurationProvider,
            UriUtils uriUtils
    ) {
        this.extractionService = extractionService;
        this.extractionCaching = extractionCaching;
        this.configurationProvider = configurationProvider;
        this.uriUtils = uriUtils;
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    public @Nonnull Multi<Void> crawlFrom(@Nonnull UUID jobUuid, @Nonnull URI uri) {
        return this.extractionCaching.cache(jobUuid.toString(), uri)
                .onItem().transformToMulti(ignoredVoid ->
                        this.crawlFrom(uri, uri, 1, new AtomicInteger(0)));
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    private @Nonnull Multi<Void> crawlFrom(
            @Nonnull URI baseDomain,
            @Nullable URI uri,
            @Nonnull Integer crrDepth,
            @Nonnull AtomicInteger crrOutbound
    ) {
        if (Objects.isNull(uri)) {
            return Multi.createFrom().empty();
        }

        // TODO - REMOVE THIS TO OUTSIDE THE LOOP
        CrawlerConfiguration crawlerConfig =
                this.configurationProvider.toDomain();

        return Uni.createFrom().item(() ->this.hasDepth(crrDepth, crawlerConfig.domainDepth()))
                .chain(depth ->
                        Boolean.FALSE.equals(depth)
                                ? Uni.createFrom().nullItem()
                                : Uni.createFrom().item(
                                        this.extractionCaching.hasNotBeenCrawled(uri)))
                .chain(crawlUri ->
                        Boolean.FALSE.equals(crawlUri)
                                ? Uni.createFrom().nullItem()
                                : Uni.createFrom().item(
                                        this.hasOutbound(uri, baseDomain,
                                                crrOutbound, crawlerConfig.domainOutbound())))
                .chain(crawlDomain ->
                        Boolean.TRUE.equals(crawlDomain)
                                ? this.scrapeFrom(uri, crawlerConfig.connectionTimeout())
                                : Uni.createFrom().nullItem())
                .onItem().ifNotNull().call(() ->
                        this.extractionCaching.cache(uri))
                .onItem().ifNotNull().call(
                        this.extractionService::persist)
                .onItem().transformToMulti(extraction ->
                        Objects.nonNull(extraction) && Objects.nonNull(extraction.getInnerUris())
                                ? Multi.createFrom().iterable(extraction.getInnerUris())
                                : Multi.createFrom().empty())
                .onItem().transformToMultiAndMerge(uriToScrape ->
                        this.crawlFrom(baseDomain, uriToScrape, crrDepth + 1, crrOutbound));
    }

    protected @Nonnull Uni<Extraction> scrapeFrom(@Nonnull URI uri, Duration timeout) {
        return Uni.createFrom().item(() -> uriUtils.validateUriConnection(uri, timeout))
                .chain(connection -> {
                    if (connection.getLeft()) {
                        return Uni.createFrom()
                                .item(() -> this.processUri(uri));
                    }

                    if (Objects.equals(connection.getRight(), 408) || Objects.equals(connection.getRight(), 504)) {
                        return Uni.createFrom().failure(new UriConnectException(
                                String.format("Error connecting to URL: %s. Will retry.", uri)));
                    }

                    return Uni.createFrom().failure(new UnreachableUriException(
                            String.format("Error connecting to URI: %s. Status: %s. No retry.", uri, connection.getRight())));
                })
                .onFailure(this::shouldRetryScraping).retry()
                    .withBackOff(MIN_BACKOFF, MAX_BACKOFF)
                    .atMost(MAX_RETRIES)
                .onFailure().transform(t -> new RuntimeException(
                        String.format("An untreatable error was encountered while scraping the URI: %s.", uri), t));
    }

    protected @Nonnull Extraction processUri(@Nonnull URI uri) {
        Document document = this.connectTo(uri);

        Extraction.ExtractionBuilder
                extractionBuilder = this.getContentFrom(document);

        extractionBuilder.innerUris(this.getUrisFrom(document));

        return extractionBuilder.build();
    }

    protected @Nonnull Extraction.ExtractionBuilder getContentFrom(@Nonnull Document document) {
        try {
            Elements metaOgTitle = document.select("meta[property=og:title]");
            String title = metaOgTitle.attr("content");

            Elements metaOgDescription = document.select("meta[property=og:description]");
            String description = metaOgDescription.attr("content");

            Element documentBody = document.body();
            String bodyHtml = documentBody.html();

            return Extraction.builder()
                    .title(title)
                    .content(bodyHtml)
                    .uri(URI.create(document.baseUri()))
                    .description(description);
        } catch (Exception e) {
            Log.errorf("An error was encountered while " +
                    "scraping document from URL: %s, for its data.", document.baseUri(), e);
            throw new WebContentExtractionException(e);
        }
    }

    protected @Nonnull Set<URI> getUrisFrom(@Nonnull Document document) {
        try {
            Elements innerUrls = document.select("a");

            return innerUrls.stream()
                    .map(url -> url.absUrl("href"))
                    .filter(url -> !url.isEmpty())
                    .filter(url -> url.startsWith("http://") || url.startsWith("https://"))
                    .map(url -> {
                        try {
                            return URI.create(url);
                        } catch (IllegalArgumentException e) {
                            Log.errorf("Ivalid URL encountered during " +
                                    "document scrape. URL will be dropped: %s.", url, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            Log.errorf("An error was encountered while scraping " +
                    "document from URL: %s, for its links.", document.baseUri(), e);
            throw new WebUriExtractionException(e);
        }
    }

    protected @Nonnull Document connectTo(@Nonnull URI uri) {
        try {
            Connection connection = Jsoup.connect(uri.toString())
                    .userAgent(USER_AGENT)
                    .referrer(CONNECTION_REFERER);

            return connection.get();
        } catch (IOException e) {
            Log.errorf("An error was encountered while connecting " +
                    "to the specified URL: %s.", uri.toString(), e);
            throw new UriConnectException(e);
        }
    }

    protected @Nonnull Boolean hasDepth(
            @Nonnull Integer crrDepth,
            @Nullable Integer depthLimit
    ) {
        if (depthLimit == null) {
            return true;
        }

        return crrDepth <= depthLimit;
    }

    protected @Nonnull Boolean hasOutbound(
            @Nonnull URI sourceUri,
            @Nonnull URI targetUri,
            @Nonnull AtomicInteger crrOutbound,
            @Nullable Integer outboundLimit
    ) {
        if (outboundLimit == null) {
            return false;
        }

        Boolean equalDomain = this.uriUtils
                .domainEquals(sourceUri, targetUri);

        return equalDomain || crrOutbound
                .getAndIncrement() < outboundLimit;
    }

    protected @Nonnull Boolean shouldRetryScraping(@Nonnull Throwable throwable) {
        return throwable instanceof UriConnectException;
    }
}
