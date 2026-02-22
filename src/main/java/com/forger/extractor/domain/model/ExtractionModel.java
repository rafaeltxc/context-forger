package com.forger.extractor.domain.model;

import lombok.*;

import java.net.URL;
import java.util.List;

@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
@AllArgsConstructor(access = AccessLevel.NONE)
public class ExtractionModel {

    private String title;

    private String content;

    private URL url;

    private String description;

    private List<String> innerUrls;
}
