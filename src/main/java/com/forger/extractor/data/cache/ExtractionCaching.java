package com.forger.extractor.data.cache;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.Objects;

@ApplicationScoped
public class ExtractionCaching {

    public Uni<Void> cache(URI uri) {
        if (Objects.isNull(uri)) {
            return Uni.createFrom().voidItem();
        }

        // TODO - COMPLETE FUNCTION LOGIC
        return null;
    }

    public Uni<Void> cache(String key, URI uri) {
        if (Objects.isNull(uri)) {
            return Uni.createFrom().voidItem();
        }

        // TODO - COMPLETE FUNCTION LOGIC
        return null;
    }

    public Boolean hasNotBeenCrawled(URI uri) {
        return null;
    }
}
