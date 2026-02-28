package com.forger.tool.timing;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;
import java.util.Optional;

public class TestCronometer implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace DURATION_NAMESPACE = ExtensionContext.Namespace.create("duration");

    @Override
    public void beforeTestExecution(ExtensionContext ctx) {
        ctx.getStore(DURATION_NAMESPACE).put("start", System.nanoTime());
    }

    @Override
    public void afterTestExecution(ExtensionContext ctx) {
        long start = Optional.ofNullable(
                ctx.getStore(DURATION_NAMESPACE).remove("start", long.class))
                .orElse(0L);

        if (start == 0L)
            return;

        long duration = System.nanoTime() - start;

        String timeoutLabel = """
                 _____       _     _____ _
                |_   _|__ __| |_  |_   _(_)_ __  ___
                  | |/ -_|_-<  _|   | | | | '  \\/ -_)
                  |_|\\___/__/\\__|   |_| |_|_|_|_\\___|
                """;

        System.out.println(timeoutLabel);

        System.out.println(ctx.getDisplayName() + " took "
                + Duration.ofNanos(duration).toSeconds() + "ns");
    }
}
