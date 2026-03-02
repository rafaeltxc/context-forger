package com.forger.api.dto;

import com.forger.extractor.domain.model.Extraction;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentDTO {

    private URI uri;

    private String title;

    private String body;

    private String description;

    public static @Nonnull ContentDTO from(
            @Nonnull Extraction extraction,
            @Nonnull String cleandUpContent
    ) {
        if (Objects.isNull(extraction.getContent()))
            throw new IllegalArgumentException("Extraction content cannot be null");

        if (Objects.isNull(extraction.getUri()))
            throw new IllegalArgumentException("Extraction url cannot be null");

        return new ContentDTO(extraction.getUri(), extraction.getTitle(),
                cleandUpContent, extraction.getDescription());
    }
}
