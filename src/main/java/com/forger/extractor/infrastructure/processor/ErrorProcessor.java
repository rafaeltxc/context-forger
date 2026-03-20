package com.forger.extractor.infrastructure.processor;

import com.forger.extractor.utils.ThreadUtils;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedInputStream;
import java.util.UUID;

@ApplicationScoped
public class ErrorProcessor {

    private final ThreadUtils threadUtils;

    public ErrorProcessor(ThreadUtils threadUtils) {
        this.threadUtils = threadUtils;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Thread logAsyncWith(UUID uuid, Throwable throwable) {
        return Thread.ofVirtual()
                .name("errorProcessor-" + uuid.toString() + "-%d")
                .uncaughtExceptionHandler(this.threadUtils.mailUncaughtException())
                .start(() -> this.logWith(uuid, throwable));
    }

    public void logWith(UUID uuid, Throwable throwable) {

    }

    private void logTo(BufferedInputStream inputStream) {

    }
}
