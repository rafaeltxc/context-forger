package com.forger.extractor.application.web.scrape;

import com.forger.extractor.application.content.PageContentProcessor;
import com.forger.extractor.data.cache.ExtractionCaching;
import com.forger.extractor.domain.model.Extraction;
import com.forger.extractor.domain.record.configuration.CrawlerConfiguration;
import com.forger.extractor.exception.*;
import com.forger.extractor.infrastructure.provider.CrawlerConfigurationProvider;
import com.forger.extractor.service.ExtractionService;
import com.forger.extractor.utils.ConnectionUtils;
import com.forger.extractor.utils.UriUtils;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Web page scraper class, with the objective gathering all the needed data for the context
 * forging.
 * <p>
 * The web page scraping is made with Mutiny asynchronous code, accelerating the page
 * content scrape. A synchronous code may be run subscribing Mutiny with only one instance.
 */
@ApplicationScoped
public class PageExtactor {

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(3);

    private static final Duration MIN_BACKOFF = Duration.ofSeconds(10);

    private static final Integer MAX_RETRIES = 3;

    private final PageContentProcessor pageContentProcessor;

    private final ExtractionService extractionService;

    private final ExtractionCaching extractionCaching;

    private final UriUtils uriUtils;

    private final ConnectionUtils connectionUtils;

    private final CrawlerConfiguration crawlerConfiguration;

    @Inject
    public PageExtactor(
            PageContentProcessor pageContentProcessor,
            ExtractionService extractionService,
            ExtractionCaching extractionCaching,
            CrawlerConfigurationProvider configurationProvider,
            UriUtils uriUtils,
            ConnectionUtils connectionUtils
    ) {
        this.pageContentProcessor = pageContentProcessor;
        this.extractionService = extractionService;
        this.extractionCaching = extractionCaching;
        this.uriUtils = uriUtils;
        this.connectionUtils = connectionUtils;

        this.crawlerConfiguration =
                configurationProvider.toDomain();
    }

    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    public @Nonnull Multi<Void> crawlFrom(
            @Nonnull UUID jobUuid,
            @Nonnull URI uri
    ) {
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

        return Uni.createFrom().item(() -> this.hasDepth(crrDepth, crawlerConfiguration.domainDepth()))
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
                                                crrOutbound, crawlerConfiguration.domainOutbound())))
                .chain(crawlDomain ->
                        Boolean.TRUE.equals(crawlDomain)
                                ? this.scrapeFrom(uri, crawlerConfiguration.connectionTimeout())
                                : Uni.createFrom().nullItem())
                .onItem()
                    .ifNotNull().call(() -> this.extractionCaching.cache(uri))
                .onItem()
                    .ifNotNull().call(this.extractionService::persist)
                .onItem()
                    .transformToMulti(extraction ->
                        Objects.nonNull(extraction) && Objects.nonNull(extraction.getInnerUris())
                                ? Multi.createFrom().iterable(extraction.getInnerUris())
                                : Multi.createFrom().empty())
                .onItem()
                    .transformToMultiAndMerge(uriToScrape ->
                        this.crawlFrom(baseDomain, uriToScrape, crrDepth + 1, crrOutbound));
    }

    protected @Nonnull Uni<Extraction> scrapeFrom(@Nonnull URI uri, Duration timeout) {
        return Uni.createFrom().item(() -> uriUtils.validateUriConnection(uri, timeout))
                .chain(connection -> {
                    if (connection.getLeft()) {
                        return Uni.createFrom()
                                .item(() -> this.processUri(uri));
                    }

                    int statusCode = connection.getRight();

                    if (statusCode == 403 || statusCode == 503 || statusCode == 429) {
                        return Uni.createFrom().failure(new BotDetectedException(
                                String.format("Erro connecting to URL: %s with status code: %s. " +
                                        "Potential Bot Challenge detected, process will be escalated to playwright.", uri, statusCode)));
                    }

                    if (Objects.equals(statusCode, 408) || Objects.equals(statusCode, 504)) {
                        return Uni.createFrom().failure(new UriConnectException(
                                String.format("Error connecting to URL: %s. Will retry.", uri)));
                    }

                    return Uni.createFrom().failure(new UnreachableUriException(
                            String.format("Error connecting to URL: %s. Status: %s. No retry.", uri, statusCode)));
                })
                .onFailure(this::shouldEscalateProcess)
                    .recoverWithItem(() -> this.processJsLoadedUri(uri))
                .onFailure(this::shouldRetryScraping).retry()
                    .withBackOff(MIN_BACKOFF, MAX_BACKOFF)
                    .atMost(MAX_RETRIES)
                .onFailure()
                    .transform(throwable -> new RuntimeException(
                        String.format("An untreatable error was encountered while scraping the URL: %s.", uri), throwable));
    }

    protected @Nonnull Extraction processUri(@Nonnull URI uri) {
        Document document = this.connectionUtils.connectTo(uri);

        if (this.pageContentProcessor.hasJsLoadedContent(document)) {
            return processJsLoadedUri(uri);
        }

        Extraction.ExtractionBuilder extractionBuilder =
                this.pageContentProcessor.getContentFrom(document);

        extractionBuilder.innerUris(
                this.pageContentProcessor.getUrisFrom(document));

        return extractionBuilder.build();
    }

    protected @Nonnull Extraction processJsLoadedUri(@Nonnull URI uri) {
        return null;
    }

    /**
     * Validates if it's possible to continue on the crawling process, based on how many
     * URIs were crawled.
     * <p>
     * Base the validation in the current crawling depth (how many times the crawler went to
     * the next URI in the page), if its lesser or equal to the configured depth
     * limitation.
     * <p>
     * If the depth limitation is not configured, it will allow the process to continue
     * indefinitely.
     *
     * @param crrDepth   Current thread crawling depth.
     * @param depthLimit Depth limitation.
     * @return If process can continue to the next URI iteration.
     */
    protected @Nonnull Boolean hasDepth(
            @Nonnull Integer crrDepth,
            @Nullable Integer depthLimit
    ) {
        if (depthLimit == null) {
            return true;
        }

        return crrDepth <= depthLimit;
    }

    /**
     * Validates if following to a new domain outside the base crawling URI is possible.
     * <p>
     * Base the validation on the crawler configured limitation. The current outbound should
     * be lesser or equal to the delimited outbound.
     * <p>
     * If the limitation is not set, it will not be allowed to the crawler to go outside the
     * original domain.
     *
     * @param sourceUri     Base for domain check.
     * @param targetUri     Target to validate domain.
     * @param crrOutbound   Current domain outbound on thread.
     * @param outboundLimit Outbound limitation.
     * @return If following to a next outside domain is permitted.
     */
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

    /**
     * Check if process should be escalated do playwright.
     *
     * @param throwable To validate exception type.
     * @return If process should be escalated.
     */
    protected @Nonnull Boolean shouldEscalateProcess(@Nonnull Throwable throwable) {
        return throwable instanceof BotDetectedException;
    }

    /**
     * Check if process should be retried based on the exception type.
     *
     * @param throwable To validate exception type.
     * @return If exception is retriable.
     */
    protected @Nonnull Boolean shouldRetryScraping(@Nonnull Throwable throwable) {
        return throwable instanceof UriConnectException;
    }
}
