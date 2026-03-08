package com.forger.extractor.service;

import com.forger.extractor.domain.model.Extraction;
import com.forger.extractor.data.repository.ExtractionRepository;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

@ApplicationScoped
public class ExtractionService {

    private final ExtractionRepository extractionRepository;

    @Inject
    public ExtractionService(ExtractionRepository extractionRepository) {
        this.extractionRepository = extractionRepository;
    }

    public @Nonnull Uni<Void> persist(@Nullable Extraction extraction) {
        if (Objects.isNull(extraction)) {
            return Uni.createFrom().voidItem();
        }

        // TODO - COMPLETE FUNCTION LOGIC
        return null;
    }
}
