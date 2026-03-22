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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@ApplicationScoped
public class PlaywrightConfigurationProvider {

    private static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(3);

    private static final Integer MAX_OPEN_PAGES = 10;

    private static final Integer MAX_OPEN_CONTEXTS = 5;

    private static final Integer JOB_QUEUE_BOUNDARY = 1000;

    private final ContextProcessor contextProcessor;

    private final CrawlerConfigurationProvider  crawlerConfigurationProvider;

    private final ThreadUtils threadUtils;

    private final AtomicInteger activeWorkers;

    private final LinkedBlockingQueue<PlaywrightJob> jobs;

    private ThreadPoolExecutor executor;

    public PlaywrightConfigurationProvider(
            ContextProcessor contextProcessor,
            CrawlerConfigurationProvider crawlerConfigurationProvider,
            ThreadUtils threadUtils
    ) {
        this.contextProcessor = contextProcessor;
        this.crawlerConfigurationProvider = crawlerConfigurationProvider;
        this.threadUtils = threadUtils;

        this.activeWorkers =
                new AtomicInteger(0);
        this.jobs =
                new LinkedBlockingQueue<>(JOB_QUEUE_BOUNDARY);
    }

    @PostConstruct
    public void setup() {
        CrawlerConfiguration crawlerConfiguration =
                crawlerConfigurationProvider.toDomain();

        // We only get half of the work force, as Playwright consumes much more heap space
        int totalWorkers = crawlerConfiguration.connectionWorkers() > 1
                ? crawlerConfiguration.connectionWorkers() / 2
                : 1;

        this.executor = this.threadUtils.getExecutor("PlaywrightJob-%d", 1, totalWorkers,
                Duration.ZERO, this.threadUtils.getErroLoggingExceptionHandler(this.contextProcessor));
    }

    @PreDestroy
    public void shutdown() {
        this.executor.shutdown();
    }

    public Extraction provide(URI uri, BiFunction<URI, BrowserContext, Extraction> job) throws InterruptedException {
        CompletableFuture<Extraction> future = new CompletableFuture<>();

        PlaywrightJob playwrightJob =
                new PlaywrightJob(job, uri, future);

        jobs.put(playwrightJob);

        this.initializeContext();

        return future.join();
    }

    public synchronized void initializeContext() {
        int maxActiveTasks = this.executor.getMaximumPoolSize();
        int missingTasks = maxActiveTasks - this.activeWorkers.get();

        for (int  i = 0; i < missingTasks; i++) {
            this.activeWorkers
                    .incrementAndGet();
            this.executor
                    .submit(new ConfigurationProviderRunner());
        }
    }

    private final class ConfigurationProviderRunner implements Runnable {

        private final ThreadPoolExecutor executor;

        private final Playwright playwright;

        private final List<Browser> browsers;

        private final List<PlaywrightJob> takenJobs;

        private final List<PlaywrightJob> defectiveJobs;

        private final Semaphore semaphore;

        private final AtomicInteger activeRunerWorkers;

        private final Object lock;

        @PreDestroy
        public void shutdown() {
            this.browsers
                    .forEach(Browser::close);
            this.playwright.close();

            this.executor.shutdown();
        }

        public ConfigurationProviderRunner() {
            this.executor = threadUtils.getExecutor(Thread.currentThread().getName() + "-RUNNER-%d",
                    1, MAX_OPEN_PAGES * MAX_OPEN_CONTEXTS, Duration.ofSeconds(20), threadUtils.getErroLoggingExceptionHandler(contextProcessor));
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
            this.activeRunerWorkers =
                    new AtomicInteger(0);
            this.lock =
                    new Object();
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    PlaywrightJob job = jobs.poll(
                            QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

                    if (Objects.isNull(job)) {
                        this.startShuttingDown();
                        break;
                    }

                    this.takenJobs.add(job);

                    CompletableFuture.runAsync(() -> {
                        this.activeRunerWorkers.incrementAndGet();

                        try {
                            this.processFrom(job);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            int runningTasks = this.activeRunerWorkers.decrementAndGet();

                            if (runningTasks == 0) {
                                synchronized (this.lock) {
                                    lock.notifyAll();
                                }
                            }
                        }
                    }, this.executor);
                }
            } catch (Exception e) {
                Log.errorf("Process terminated unexpectedly " +
                        "while scraping page with Playwright.", e);
                contextProcessor.saveErrorFrom(e);
            } finally {
                for (PlaywrightJob job : this.takenJobs) {
                    if (!job.future().isDone()) {
                        // TODO - Job not completed due exception. Add to the execution queue again.
                    }
                }

                for (PlaywrightJob job : this.defectiveJobs) {
                    // TODO - Defective job not completed. Retry process only one time to be sure.
                }

                this.executor.shutdown();

                activeRunerWorkers.decrementAndGet();

                if (!Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void startShuttingDown() throws InterruptedException {
            synchronized (this.lock) {
                while (this.activeRunerWorkers.intValue() > 0) {
                    this.lock.wait();
                }
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

        private void retryFrom(PlaywrightJob applier) throws InterruptedException {
            try {
                Extraction extraction = applier.job().apply(
                        applier.uri(), this.contextFrom(this.playwright, this.browsers));

                applier.future()
                        .complete(extraction);
            } catch (Exception e) {
                Log.errorf("An error was caught " +
                        "retrying scraping the URL: %s.", applier.uri());

                contextProcessor.saveErrorFrom(e);
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
