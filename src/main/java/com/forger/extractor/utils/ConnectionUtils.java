package com.forger.extractor.utils;

import com.forger.extractor.exception.UriConnectException;
import io.quarkus.logging.Log;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;

@ApplicationScoped
public class ConnectionUtils {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 11.0; " +
            "Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36";

    private static final String CONNECTION_REFERER = "http://www.google.com";

    /**
     * Connect to a received URI with Jsoup.
     *
     * @param uri URI to be connected to.
     * @return Page connection.
     */
    public @Nonnull Document connectTo(@Nonnull URI uri) {
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
}
