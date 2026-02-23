package com.forger;

import lombok.Getter;

public enum TestContainerImages {

    NGINX("nginx:1.29.5-alpine-slim")
    ;

    @Getter
    final String image;

    TestContainerImages(String image) {
        this.image = image;
    }
}
