package com.forger.extractor.application.web;

import com.forger.extractor.model.ExtractionModel;
import com.forger.extractor.exception.UnreachableUriException;
import com.forger.extractor.exception.UriConnectException;
import com.forger.extractor.exception.WebContentExtractionException;
import com.forger.extractor.exception.WebUriExtractionException;
import com.forger.extractor.utils.UriUtils;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import java.util.stream.Collectors;

/**
 * Web page scraper class, with the objective gathering all the needed data for the context
 * forging.
 * <p>
 * The web page scraping is made with Mutiny asynchronous code, accelerating the page
 * content scrape. A synchronous code may be run subscribing Mutiny with only one instance.
 */
@ApplicationScoped
public class WebScraper {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 11.0; " +
            "Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36";

    private static final String CONNECTION_REFERER = "http://www.google.com";

    private final UriUtils uriUtils;

    @ConfigProperty(name = "forger.connection.timeout")
    Integer connectionTimeout;

    @Inject
    public WebScraper(UriUtils  uriUtils) {
        this.uriUtils = uriUtils;
    }

    public @Nonnull Uni<ExtractionModel> scrapeUriAsync(URI uri) {
        return Uni.createFrom().item(this.scrapeUri(uri))
                .onSubscription()
                .invoke(() -> {
                    Pair<Boolean, Integer> connection = uriUtils.validateUriConnection(
                            uri, Duration.ofSeconds(connectionTimeout));

                    if (connection.getLeft())
                        return;

                    if (Objects.equals(connection.getRight(), 408) || Objects.equals(connection.getRight(), 504))
                        throw new UriConnectException(String.format("An error was encountered " +
                                "while connecting to the specified URL: %s. Newer attempts will be made.", uri));

                    throw new UnreachableUriException(String.format("An error was encountered while " +
                            "connecting to the URI: %s. Connection will not be tried again.", connection.getRight()));
                })
                .onFailure().transform(t -> {
                    if (this.retryScrapeAttempt(t))
                        return t;

                    return new RuntimeException(String.format("An untreatable " +
                            "error was encountered while scraping the URI: %s.", uri), t);
                })
                .onFailure().retry()
                .withBackOff(Duration.ofSeconds(3), Duration.ofSeconds(10))
                .atMost(3);
    }

    protected @Nonnull ExtractionModel scrapeUri(@Nonnull URI uri) {
        Document document = this.connectTo(uri);

        ExtractionModel.ExtractionModelBuilder
                extractionBuilder = this.getDataFrom(document);

        extractionBuilder.innerUris(this.getUrisFrom(document));

        return extractionBuilder.build();
    }

    protected @Nonnull ExtractionModel.ExtractionModelBuilder getDataFrom(@Nonnull Document document) {
        try {
            Elements metaOgTitle = document.select("meta[property=og:title]");
            String title = metaOgTitle.attr("content");

            Elements metaOgDescription = document.select("meta[property=og:description]");
            String description = metaOgDescription.attr("content");

            Element documentBody = document.body();
            String bodyHtml = documentBody.html();

            return ExtractionModel.builder()
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

    private @Nonnull Boolean retryScrapeAttempt(@Nonnull Throwable throwable) {
        return throwable instanceof UriConnectException;
    }
}
