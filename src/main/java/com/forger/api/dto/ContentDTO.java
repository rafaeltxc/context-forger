package com.forger.api.dto;

import com.forger.extractor.model.ExtractionModel;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URL;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentDTO {

    private URL url;

    private String title;

    private String body;

    private String description;

    public static @Nonnull ContentDTO from(
            @Nonnull ExtractionModel extractionModel,
            @Nonnull String cleandUpContent
    ) {
        if (Objects.isNull(extractionModel.getContent()))
            throw new IllegalArgumentException("Extraction content cannot be null");

        if (Objects.isNull(extractionModel.getUrl()))
            throw new IllegalArgumentException("Extraction url cannot be null");

        return new ContentDTO(extractionModel.getUrl(), extractionModel.getTitle(),
                cleandUpContent, extractionModel.getDescription());
    }
}
