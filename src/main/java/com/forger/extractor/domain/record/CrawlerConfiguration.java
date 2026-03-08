package com.forger.extractor.domain.record;

import java.time.Duration;

public record CrawlerConfiguration(
    Duration connectionTimeout,
    int connectionWorkers,
    int domainOutbound,
    int domainDepth
) {}
