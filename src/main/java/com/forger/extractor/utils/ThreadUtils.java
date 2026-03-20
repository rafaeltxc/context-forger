package com.forger.extractor.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.time.Duration;
import java.util.concurrent.*;

@ApplicationScoped
public class ThreadUtils {

    public Thread.UncaughtExceptionHandler mailUncaughtException() {
        // TODO - create mail system to send data on exceptions.
        return (runnable, throwable) -> {};
    }

    public ThreadPoolExecutor getExecutor(
            String threadName,
            Integer min,
            Integer max,
            Duration keepAlive
    ) {
        ThreadFactory threadFactory = BasicThreadFactory.builder()
                .namingPattern(threadName)
                .build();

        return new ThreadPoolExecutor(min, max, keepAlive.toMillis(), TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),  threadFactory, this.getSilentRejectedExecutionHandler());
    }

    public RejectedExecutionHandler getSilentRejectedExecutionHandler() {
        return ((runnable, threadPoolExecutor) -> {
            if (!threadPoolExecutor.isShutdown()) {
                try {
                    threadPoolExecutor.getQueue().put(runnable);
                } catch (InterruptedException e) {
                    throw new RejectedExecutionException(e);
                }
            }
        });
    }
}
