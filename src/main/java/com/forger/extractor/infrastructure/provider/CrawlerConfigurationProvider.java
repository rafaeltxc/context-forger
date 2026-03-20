package com.forger.extractor.infrastructure.provider;

import com.forger.extractor.domain.record.configuration.CrawlerConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class CrawlerConfigurationProvider {

    @ConfigProperty(name = "forger.connection.timeout")
    int connectionTimeout;

    @ConfigProperty(name = "forger.connection.workers")
    int connectionWorkers;

    @ConfigProperty(name = "forger.domain.follows")
    int domainOutbound;

    @ConfigProperty(name = "forger.domain.deepness")
    int domainDepth;

    public CrawlerConfiguration toDomain() {
        Duration connectionTimeout = Duration.ofSeconds(this.connectionTimeout);

        return new CrawlerConfiguration(connectionTimeout,
                this.connectionWorkers, this.domainOutbound, this.domainDepth);
    }
}
