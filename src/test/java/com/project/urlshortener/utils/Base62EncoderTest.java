package com.project.urlshortener.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encode_zero_returnsFirstCharacter() {
        assertThat(encoder.encode(0)).isEqualTo("a");
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 61L, 62L, 63L, 100L, 9999L, 1_000_000L})
    void encode_decode_roundTrip(long id) {
        assertThat(encoder.decode(encoder.encode(id))).isEqualTo(id);
    }

    @Test
    void encode_62_firstTwoCharacterCode() {
        // 62 is the first value that needs two characters
        String code = encoder.encode(62);
        assertThat(code).hasSize(2);
        assertThat(encoder.decode(code)).isEqualTo(62L);
    }

    @Test
    void decode_invalidCharacter_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> encoder.decode("abc!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid character");
    }

    @Test
    void decode_emptyString_returnsZero() {
        assertThat(encoder.decode("")).isEqualTo(0L);
    }
}