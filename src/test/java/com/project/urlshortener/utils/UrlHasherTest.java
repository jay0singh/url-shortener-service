package com.project.urlshortener.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlHasherTest {

    private final UrlHasher hasher = new UrlHasher();

    @Test
    void hash_producesValidSha256HexString() {
        String result = hasher.hash("https://example.com");
        assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void hash_isDeterministic() {
        assertThat(hasher.hash("https://example.com"))
                .isEqualTo(hasher.hash("https://example.com"));
    }

    @Test
    void hash_differentInputs_produceDifferentHashes() {
        assertThat(hasher.hash("https://a.com"))
                .isNotEqualTo(hasher.hash("https://b.com"));
    }
}
