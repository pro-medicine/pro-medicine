package com.ucamp.app.domain.chat.vector;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// BLOB(float32 little-endian x 1536, numpy tobytes) 변환과 정규화 (설계 5-1)
class VectorMathTest {

    static byte[] bytes(float[] v, ByteOrder order) {
        ByteBuffer b = ByteBuffer.allocate(v.length * Float.BYTES).order(order);
        for (float x : v) {
            b.putFloat(x);
        }
        return b.array();
    }

    @Test
    void little_endian으로_읽는다() {
        float[] v = new float[InMemoryReviewVectorIndex.DIMENSIONS];
        v[0] = 0.25f;
        v[1535] = -1.5f;

        float[] little = InMemoryReviewVectorIndex.decode(bytes(v, ByteOrder.LITTLE_ENDIAN));
        float[] big = InMemoryReviewVectorIndex.decode(bytes(v, ByteOrder.BIG_ENDIAN));

        assertThat(little[0]).isEqualTo(0.25f);
        assertThat(little[1535]).isEqualTo(-1.5f);
        assertThat(big[0]).isNotEqualTo(0.25f);
    }

    @Test
    void 길이가_6144바이트가_아니면_null() {
        assertThat(InMemoryReviewVectorIndex.decode(new byte[6140])).isNull();
        assertThat(InMemoryReviewVectorIndex.decode(null)).isNull();
    }

    @Test
    void 정규화하면_내적이_코사인() {
        float[] a = InMemoryReviewVectorIndex.normalize(new float[]{3, 4});
        float[] b = InMemoryReviewVectorIndex.normalize(new float[]{4, 3});

        assertThat(InMemoryReviewVectorIndex.dot(a, a)).isCloseTo(1.0, within(1e-6));
        assertThat(InMemoryReviewVectorIndex.dot(a, b)).isCloseTo(24.0 / 25.0, within(1e-6));
        assertThat(InMemoryReviewVectorIndex.normalize(new float[]{0, 0})).isNull();
    }
}
