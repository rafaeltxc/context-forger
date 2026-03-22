package com.forger.extractor.utils;

import com.forger.extractor.infrastructure.processor.ContextProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

@ApplicationScoped
public class ThreadUtils {

    public Thread.UncaughtExceptionHandler getErroLoggingExceptionHandler(ContextProcessor contextProcessor) {
        return this.getGenericExceptionHandler(contextProcessor::saveErrorFrom);
    }

    public Thread.UncaughtExceptionHandler getGenericExceptionHandler(Consumer<Throwable> consumer) {
        return (runnable, throwable) -> {
            if (Objects.nonNull(consumer)) {
                consumer.accept(throwable);
            }

            runnable.interrupt();
        };
    }

    public ThreadPoolExecutor getExecutor(
            String threadName,
            Integer min,
            Integer max,
            Duration keepAlive,
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler
    ) {
        BasicThreadFactory.Builder threadFactory = BasicThreadFactory.builder()
                .namingPattern(threadName);

        if (Objects.nonNull(uncaughtExceptionHandler)) {
            threadFactory.uncaughtExceptionHandler(uncaughtExceptionHandler);
        }

        return new ThreadPoolExecutor(min, max, keepAlive.toMillis(), TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),  threadFactory.build(), this.getSilentRejectedExecutionHandler());
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
