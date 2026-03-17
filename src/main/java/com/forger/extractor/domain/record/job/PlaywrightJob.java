package com.forger.extractor.domain.record.job;

import com.forger.extractor.domain.model.Extraction;
import com.microsoft.playwright.BrowserContext;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public record PlaywrightJob(
        UUID uuid,
        BiConsumer<URI, BrowserContext> job,
        URI uri,
        CompletableFuture<Extraction> future
) {
}
