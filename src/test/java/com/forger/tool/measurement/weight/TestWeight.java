package com.forger.tool.measurement.weight;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AllArgsConstructor(access = AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestWeight {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Tag("very_slow")
    public @interface VerySlowTest {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Tag("slow")
    public @interface SlowTest {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Tag("fast")
    public @interface FastTest {}
}
