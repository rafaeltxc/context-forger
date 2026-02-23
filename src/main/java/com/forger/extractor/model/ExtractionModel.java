package com.forger.extractor.model;

import lombok.*;

import java.net.URI;
import java.util.Set;

@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
@AllArgsConstructor(access = AccessLevel.NONE)
public class ExtractionModel {

    private String title;

    private String content;

    private URI url;

    private String description;

    private Set<URI> innerUris;
}
