package com.forger.extractor.infrastructure;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@ApplicationScoped
public class PlaywrightConfigurationProvider {

    private final static Integer MAX_OPEN_PAGES = 100;
    private final static Integer MAX_OPEN_CONTEXTS = 5;

    protected LinkedBlockingDeque<Function<BrowserContext, ?>> jobs = new LinkedBlockingDeque<>();

    private final class ConfigurationProviderRunner extends Thread {

        private final Playwright playwright;

        private final List<Browser> browsers;

        private AtomicBoolean acquirable;

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

            this.acquirable =
                    new AtomicBoolean(true);
        }

        @Override
        public void run() {
            try {
                while (!this.isInterrupted()) {
                    this.processFrom(jobs.take());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void pauseAcquisitions() {
            this.acquirable.set(false);
        }

        private void freeAcquisitions() {
            this.acquirable.set(true);
        }

        private void processFrom(Function<BrowserContext, ?> job) {
            job.apply(this.contextFrom(this.playwright, this.browsers));
        }

        private BrowserContext contextFrom(
                Playwright playwright,
                List<Browser> browsers
        ) {
            if (browsers.isEmpty()) {
                return this.updateBrowserFrom(playwright, browsers);
            }

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

            // If we got here, mean we reached context limitation.
            this.pauseAcquisitions();

            // Repeat the process until a context is open.

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
