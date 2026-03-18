package com.forger.extractor.domain.record.job;

import com.forger.extractor.domain.model.Extraction;
import com.microsoft.playwright.BrowserContext;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public record PlaywrightJob(
        UUID uuid,
        BiFunction<URI, BrowserContext, Extraction> job,
        URI uri,
        CompletableFuture<Extraction> future
) {
}
