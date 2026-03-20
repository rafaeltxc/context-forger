package com.forger.extractor.infrastructure.processor;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;

import java.util.UUID;

@RequestScoped
public class ContextProcessor {

    private final ErrorProcessor errorProcessor;

    private UUID contextUuid;

    public ContextProcessor(ErrorProcessor errorProcessor) {
        this.errorProcessor = errorProcessor;
    }

    @PostConstruct
    public void setup() {
        this.contextUuid = UUID.randomUUID();
    }

    public void saveErrorFrom(Throwable throwable) {
        this.errorProcessor
                .logAsyncWith(this.contextUuid, throwable);
    }
}
