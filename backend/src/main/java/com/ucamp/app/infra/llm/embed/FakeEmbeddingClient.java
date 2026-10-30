package com.ucamp.app.infra.llm.embed;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * API 없이 쓰는 가짜 임베딩 (기본 프로필·테스트). 같은 문장 → 같은 벡터 (문장 해시를 시드로 1536차원, L2 정규화).
 * 테스트는 setVector로 특정 문장에 특정 벡터(예: 실제 리뷰 언급 벡터)를 줄 수 있다.
 */
public class FakeEmbeddingClient implements EmbeddingClient {
    public static final int DIMENSIONS = 1536;

    private final String model;
    private final Map<String, float[]> fixed = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    public FakeEmbeddingClient(String model) {
        this.model = model;
    }

    @Override
    public Embedding embed(String text) {
        requests.add(text);
        if (failure != null) {
            throw failure;
        }
        float[] vector = fixed.get(text);
        return new Embedding(vector != null ? vector.clone() : hashVector(text), model,
                Math.max(1, text.length() / 3), 0, null);
    }

    @Override
    public String model() {
        return model;
    }

    public void setVector(String text, float[] vector) {
        fixed.put(text, vector.clone());
    }

    /** 다음 호출부터 예외를 던진다 (null이면 해제) */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        fixed.clear();
        requests.clear();
        failure = null;
    }

    static float[] hashVector(String text) {
        long seed = 1125899906842597L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            seed = 31 * seed + b;
        }
        SplittableRandom random = new SplittableRandom(seed);
        float[] v = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = (float) random.nextGaussian();
            norm += v[i] * v[i];
        }
        float inv = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] *= inv;
        }
        return v;
    }
}
