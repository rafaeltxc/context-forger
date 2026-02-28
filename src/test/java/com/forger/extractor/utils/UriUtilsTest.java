package com.forger.extractor.utils;

import com.forger.tool.nginx.AbstractNginxTestContainer;
import com.forger.tool.measurement.TestDuration;
import com.forger.tool.measurement.TestWheigt;
import com.google.common.collect.ImmutableList;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.gradle.internal.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

@QuarkusTest
@TestWheigt(TestDuration.VERY_FAST)
public class UriUtilsTest extends AbstractNginxTestContainer {

    @Inject
    UriUtils uriUtils;

    private static List<Pair<Boolean, String>> definedTestUrls() {
        return ImmutableList.of(
            // ✅ Valid Absolute URLs
            Pair.of(true, "https://www.example-testing-domain.com/path/to/resource.html"),
            Pair.of(true, "http://subdomain.fake-url.org:8080/api/v1/users?id=123&status=active"),
            Pair.of(true, "ftp://testuser:securepassword@ftp.madeup-server.net/downloads/file.zip"),
            Pair.of(true, "https://anothertest.io/about#team-section"),
            Pair.of(false, "mailto:hello@thisisnotarealemail.com"),
            Pair.of(false, "wss://websocket.test-server.dev/stream"),

            // ❌ Valid Partial/Relative URLs (Fails strict absolute URL validation)
            Pair.of(false, "/assets/images/logo-v2.png"),
            Pair.of(false, "../styles/main.css"),
            Pair.of(false, "?category=shoes&sort=price_asc"),
            Pair.of(false, "#contact-form"),

            // ❌ Invalid / Malformed URLs
            Pair.of(false, "htt://bad-scheme.com/typo"),
            Pair.of(false, "https://www.domain with spaces.com"),
            Pair.of(false, "just-a-random-string-without-context"),
            Pair.of(false, "://missing-everything-but-the-colon.com"),
            Pair.of(false, "http://www.double-dot..com/"),
            Pair.of(false, "https://example.com/path_with_unencoded_character_<br>"),
            Pair.of(false, "http://[invalid-ipv6-format]/"),
            Pair.of(false, "https://user:pass@/missing-host.com")
        );
    }

    @Test
    @DisplayName("Check the total count of valid URIs")
    public void testTotalValidUris() {
        List<Pair<Boolean, String>> urisList = definedTestUrls();

        int totalUris = (int) urisList.stream()
                .filter((pair -> Boolean.TRUE.equals(pair.getLeft())))
                .count();

        int counter = 0;
        for (Pair<Boolean, String> pair : urisList) {
            if (this.uriUtils.validateUri(pair.getRight()))
                counter++;
        }

        Assertions.assertEquals(totalUris, counter);
    }

    @ParameterizedTest()
    @MethodSource("definedTestUrls")
    @DisplayName("Test valid and invalid URIs")
    public void testValidUri(Pair<Boolean, String> testPair) {
        Boolean isUriValid = this.uriUtils
                .validateUri(testPair.getRight());

        Assertions.assertEquals(isUriValid, testPair.getLeft());
    }

    @ParameterizedTest()
    @NullAndEmptySource
    @DisplayName("Test empty and null URIs")
    public void testEmptyUri(String testUri) {
        Assertions.assertEquals(false, uriUtils.validateUri(testUri));
    }

    @Test
    public void testInvalidUri() {}
}
