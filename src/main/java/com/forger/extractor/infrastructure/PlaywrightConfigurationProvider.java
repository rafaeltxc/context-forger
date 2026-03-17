package com.forger.extractor.infrastructure;

import com.forger.extractor.domain.model.Extraction;
import com.forger.extractor.domain.record.configuration.CrawlerConfiguration;
import com.forger.extractor.domain.record.job.PlaywrightJob;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

@ApplicationScoped
public class PlaywrightConfigurationProvider {

    private final static Integer MAX_OPEN_PAGES = 10;

    private final static Integer MAX_OPEN_CONTEXTS = 5;

    private final CrawlerConfigurationProvider crawlerConfigurationProvider;

    protected LinkedBlockingDeque<PlaywrightJob> jobs;

    protected List<Thread> threads;

    public PlaywrightConfigurationProvider(CrawlerConfigurationProvider crawlerConfigurationProvider) {
        this.crawlerConfigurationProvider = crawlerConfigurationProvider;

        this.jobs = new LinkedBlockingDeque<>();
    }

    public Extraction provide(URI uri, BiConsumer<URI, BrowserContext> job) {
        this.initializeContext();

        UUID uuid = UUID.randomUUID();

        CompletableFuture<Extraction> future =new CompletableFuture<>();

        PlaywrightJob playwrightJob =
                new PlaywrightJob(uuid, job, uri, future);

        jobs.offer(playwrightJob);

        return future.join();
    }

    public void initializeContext() {
        if (Objects.nonNull(this.threads) && !this.threads.isEmpty()) {
            return;
        }

        CrawlerConfiguration configuration =
                this.crawlerConfigurationProvider.toDomain();

        // We only get half of the work force, as Playwright consumes much more heap space
        int totalWorkers = configuration.connectionWorkers() > 1
                ? configuration.connectionWorkers() / 2
                : 1;

        for (int i = 0; i < totalWorkers; i++) {
            this.threads.add(
                    Thread.ofPlatform()
                            .name(String.format("PlaywrightJob-%d", i))
                            // TODO - Create uncaught exception handler to fallback
                            .start(new ConfigurationProviderRunner()));
        }
    }

    private final class ConfigurationProviderRunner implements Runnable {

        private final Playwright playwright;

        private final List<Browser> browsers;

        private final Semaphore semaphore;

        @PreDestroy
        public void shutdown() {
            this.browsers
                    .forEach(Browser::close);
            this.playwright.close();
        }

        public ConfigurationProviderRunner() {
            this.playwright =
                    Playwright.create();
            this.browsers =
                    new CopyOnWriteArrayList<>();

            this.semaphore =
                    new Semaphore(MAX_OPEN_PAGES * MAX_OPEN_CONTEXTS);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    this.processFrom(jobs.take());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void processFrom(PlaywrightJob applier) throws InterruptedException {
            applier.job().accept(applier.uri(), this.contextFrom(this.playwright, this.browsers));
        }

        private BrowserContext contextFrom(
                Playwright playwright,
                List<Browser> browsers
        ) throws InterruptedException {
            if (browsers.isEmpty()) {
                return this.updateBrowserFrom(playwright, browsers);
            }

            semaphore.acquire();

            for (Browser browser : browsers) {
                BrowserContext context = this.hasAvailableContext(browser);
                if (Objects.nonNull(context)) {
                    return context;
                }

                // If no context has space but number of contexts isn't exceeded.
                if (browser.contexts().size() < MAX_OPEN_CONTEXTS) {
                    return browser.newContext();
                }
            }

            // We should not be here, the error will be silently exposed and fallback.

            // TODO - Fallback to scrape wrongly positioned page, not lost data.
            return null;
        }

        private BrowserContext hasAvailableContext(Browser browser) {
            for (BrowserContext context : browser.contexts()) {
                if (context.pages().size() < MAX_OPEN_PAGES) {
                    return context;
                }
            }

            return null;
        }

        private BrowserContext updateBrowserFrom(
                Playwright playwright,
                List<Browser> browsers
        ) {
            Browser browser = playwright
                    .chromium()
                    .launch();

            browsers.add(browser);

            return browser.newContext();
        }
    }
}
