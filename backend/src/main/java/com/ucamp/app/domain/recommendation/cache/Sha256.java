package com.ucamp.app.domain.recommendation.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 결과 캐시 키와 기준 데이터 버전(기준표·규칙)에 쓰는 SHA-256 해시
public final class Sha256 {
    /** 데이터 버전 표시용 짧은 해시 길이 (앞 6바이트 = 16진수 12자) */
    private static final int SHORT_BYTES = 6;

    private Sha256() {
    }

    public static String hex(String text) {
        return HexFormat.of().formatHex(digest(text));
    }

    public static String shortHex(String text) {
        return HexFormat.of().formatHex(digest(text), 0, SHORT_BYTES);
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
