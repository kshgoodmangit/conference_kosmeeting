package com.bjworld21.congress.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingBinaryConverterTest {

    @Test
    void convertsFloatVectorToBytesAndBack() {
        float[] original = {0.25f, -0.5f, 1.0f, 0.0f};

        byte[] bytes = EmbeddingBinaryConverter.toBytes(original);
        float[] restored = EmbeddingBinaryConverter.fromBytes(bytes);

        assertThat(bytes).hasSize(original.length * Float.BYTES);
        assertThat(restored).containsExactly(original);
    }

    @Test
    void rejectsInvalidByteLength() {
        assertThatThrownBy(() -> EmbeddingBinaryConverter.fromBytes(new byte[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
