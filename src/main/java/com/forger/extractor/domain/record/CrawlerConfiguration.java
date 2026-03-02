package com.forger.extractor.domain.record;

public record CrawlerConfiguration(
    int connectionTimeout,
    int connectionTWorkers,
    int domainFollows,
    int domainDeepness
) {}
