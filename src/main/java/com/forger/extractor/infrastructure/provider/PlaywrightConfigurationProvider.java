package com.forger.extractor.infrastructure.provider;

import com.forger.extractor.domain.model.Extraction;
import com.forger.extractor.domain.record.configuration.CrawlerConfiguration;
import com.forger.extractor.domain.record.job.PlaywrightJob;
import com.forger.extractor.infrastructure.processor.ContextProcessor;
import com.forger.extractor.utils.ThreadUtils;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BiFunction;

@ApplicationScoped
public class PlaywrightConfigurationProvider {

    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(30);

    private static final Integer MAX_OPEN_PAGES = 10;

    private static final Integer MAX_OPEN_CONTEXTS = 5;

    private final ContextProcessor contextProcessor;

    private final ThreadUtils threadUtils;

    private final CrawlerConfiguration crawlerConfiguration;

    protected final List<Thread> threads;

    protected LinkedBlockingQueue<PlaywrightJob> jobs;

    public PlaywrightConfigurationProvider(
            ContextProcessor contextProcessor,
            CrawlerConfigurationProvider crawlerConfigurationProvider,
            ThreadUtils threadUtils
    ) {
        this.contextProcessor = contextProcessor;
        this.threadUtils = threadUtils;

        this.crawlerConfiguration = crawlerConfigurationProvider.toDomain();
        this.jobs = new LinkedBlockingQueue<>();
        this.threads = new ArrayList<>();
    }

    public Extraction provide(URI uri, BiFunction<URI, BrowserContext, Extraction> job) {
        this.initializeContext();

        UUID uuid = UUID.randomUUID();

        CompletableFuture<Extraction> future = new CompletableFuture<>();

        PlaywrightJob playwrightJob =
                new PlaywrightJob(uuid, job, uri, future);

        jobs.offer(playwrightJob);

        return future.join();
    }

    public void initializeContext() {
        // We only get half of the work force, as Playwright consumes much more heap space
        int totalWorkers = this.crawlerConfiguration.connectionWorkers() > 1
                ? this.crawlerConfiguration.connectionWorkers() / 2
                : 1;

        if (Objects.nonNull(this.threads)
                && this.threads.size() >= totalWorkers) {
            return;
        }

        int totalThreads = this.threads.size() - totalWorkers;

        synchronized (this.threads) {
            for (int i = 0; i < totalThreads; i++) {
                this.threads.add(Thread.ofPlatform()
                        .name(String.format("PlaywrightJob-%d", i))
                        .uncaughtExceptionHandler((_, throwable) -> {
                            this.threads
                                    .removeIf(Thread::isInterrupted);

                            this.contextProcessor
                                    .saveErrorFrom(throwable);
                        })
                        .start(new ConfigurationProviderRunner()));
            }
        }
    }

    private final class ConfigurationProviderRunner implements Runnable {

        private final ThreadPoolExecutor executor;

        private final Playwright playwright;

        private final List<Browser> browsers;

        private final List<PlaywrightJob> takenJobs;

        private final List<PlaywrightJob> defectiveJobs;

        private final Semaphore semaphore;

        @PreDestroy
        public void shutdown() {
            this.browsers
                    .forEach(Browser::close);
            this.playwright.close();

            this.executor.shutdown();
        }

        public ConfigurationProviderRunner() {
            this.executor = threadUtils.getExecutor(Thread.currentThread().getName() + "-RUNNER-%d",
                    1, MAX_OPEN_PAGES * MAX_OPEN_CONTEXTS, Duration.ofSeconds(20));
            this.playwright =
                    Playwright.create();
            this.browsers =
                    new CopyOnWriteArrayList<>();
            this.takenJobs =
                    new CopyOnWriteArrayList<>();
            this.defectiveJobs =
                    new CopyOnWriteArrayList<>();
            this.semaphore =
                    new Semaphore(MAX_OPEN_PAGES * MAX_OPEN_CONTEXTS);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    PlaywrightJob job = jobs.poll(
                            QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

                    if (Objects.isNull(job)) {
                        break;
                    }

                    this.takenJobs.add(job);

                    CompletableFuture.runAsync(() -> {
                        try {
                            this.processFrom(job);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, this.executor);
                }
            } catch (Exception e) {
                Log.errorf("Process terminated unexpectedly " +
                        "while scraping page with Playwright.", e);
                contextProcessor.saveErrorFrom(e);
            } finally {
                // TODO - Thread fallback for failed jobs.
            }
        }

        private void processFrom(PlaywrightJob applier) throws InterruptedException {
            try {
                Extraction extraction = applier.job().apply(
                        applier.uri(), this.contextFrom(this.playwright, this.browsers));

                applier.future()
                        .complete(extraction);

                this.takenJobs
                        .remove(applier);
            } catch (Exception e) {
                Log.errorf("An error was caught scraping the " +
                        "URL: %s. Jobs will be saved for retrying.", applier.uri());

                contextProcessor.saveErrorFrom(e);

                this.takenJobs
                        .remove(applier);
                this.defectiveJobs
                        .add(applier);
            }
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
            throw new IllegalStateException("Running threads " +
                    "were not hold by the logic gate. Problematic code logic.");
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
