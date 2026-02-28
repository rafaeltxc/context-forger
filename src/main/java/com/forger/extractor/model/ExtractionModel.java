package com.forger.extractor.model;

import lombok.*;

import java.net.URI;
import java.util.Set;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionModel {

    private String title;

    private String content;

    private URI uri;

    private String description;

    private Set<URI> innerUris;
}
