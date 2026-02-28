package com.forger.tool.measurement;

import lombok.Getter;

public enum TestDuration {

    VERY_SLOW(10),
    SLOW(7),
    MID(5),
    FAST(3),
    VERY_FAST(1)
    ;

    @Getter
    final Integer average;

    TestDuration(Integer average) {
        this.average = average;
    }
}
