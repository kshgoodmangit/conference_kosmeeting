package com.bjworld21.congress.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EmbeddingBinaryConverter {
    private EmbeddingBinaryConverter() {
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer
                .allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Invalid embedding byte length.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
