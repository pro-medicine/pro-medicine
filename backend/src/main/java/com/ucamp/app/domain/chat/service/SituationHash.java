package com.ucamp.app.domain.chat.service;

import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;

/**
 * V5 같은 상황 재사용: 파싱 결과 + 안전 입력 + data_version → SHA-256 해시.
 * 키 정렬로 순서를 고정해 같은 입력이면 같은 해시가 나온다.
 */
public final class SituationHash {
    private SituationHash() {
    }

    /**
     * @param parse       LLM 파싱 결과 (searchQuery, medications, conditions)
     * @param input       안전 입력 (프로필 약·질환·임신 포함)
     * @param dataVersion 리뷰 데이터 버전
     */
    public static String compute(ChatParse parse, RecommendationInput input, LocalDateTime dataVersion) {
        TreeMap<String, String> map = new TreeMap<>();
        map.put("searchQuery", safe(parse.searchQuery()));
        map.put("parseMedications", sorted(parse.medications().stream()
                .map(m -> m.name() + ":" + m.drugClass()).toList()));
        map.put("parseConditions", sorted(parse.conditions()));
        map.put("inputMedications", sorted(input.medicationsOrEmpty().stream()
                .map(m -> m.drugId() + ":" + m.drugClass() + ":" + m.name()).toList()));
        map.put("inputConditions", sorted(input.conditionsOrEmpty()));
        map.put("pregnancy", safe(input.pregnancy()));
        map.put("dataVersion", dataVersion == null ? "" : dataVersion.toString());
        return sha256(map.toString());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sorted(List<String> items) {
        return items.stream().sorted().toList().toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
