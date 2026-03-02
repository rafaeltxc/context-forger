package com.forger.extractor.domain.model;

import lombok.*;

import java.net.URI;
import java.util.Set;

@Builder
@Getter
@AllArgsConstructor
public class Extraction {

    private String title;

    private String content;

    private URI uri;

    private String description;

    private Set<URI> innerUris;
}
