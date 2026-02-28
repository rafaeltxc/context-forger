package com.forger.extractor.utils;

import com.forger.extractor.exception.UriConnectException;
import io.quarkus.logging.Log;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ApplicationScoped
public class UriUtils {

    public @Nonnull Boolean validateUri(@Nullable String uri) {
        UrlValidator urlValidator = new
                UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

        return urlValidator.isValid(uri);
    }

    public @Nonnull Pair<Boolean, Integer> validateUriConnection(@Nullable URI uri, @Nonnull Duration timeout) {
        if (Objects.isNull(uri))
            throw new UriConnectException("URI can " +
                    "not be null on connection validation");

        try {
            Connection connection = Jsoup.connect(uri.toString())
                    .timeout((Math.toIntExact(timeout.toMillis())))
                    .followRedirects(false);

            Connection.Response response = connection.execute();

            return Pair.of(Objects.equals(
                    response.statusCode(), 200), response.statusCode());
        } catch (IOException e) {
            Log.errorf("An error was encountered while validating " +
                    "connection. Connection was not concluded for URL: %s", uri, e);
            throw new UriConnectException(e);
        }
    }
}
