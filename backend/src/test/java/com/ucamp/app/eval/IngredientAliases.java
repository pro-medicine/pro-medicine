package com.ucamp.app.eval;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM이 준 성분 이름 → 성분 코드 (eval/ingredient-aliases.json).
 * 1) 정규화(소문자, 공백·하이픈·괄호·점·따옴표 제거)한 이름이 별칭과 같으면 그 코드
 * 2) 아니면 3글자 이상 별칭 중 긴 것부터 이름에 포함되는지 본다 ("비타민B12"가 "비타민B1"보다 먼저)
 * 못 찾으면 null (평가에서는 허용 밖 성분으로 센다)
 */
public class IngredientAliases {
    private static final int MIN_CONTAINS_LENGTH = 3;

    private final Map<String, String> exact = new HashMap<>();
    private final List<Map.Entry<String, String>> containsOrder = new ArrayList<>();
    private final Map<String, String> displayNames = new LinkedHashMap<>();

    public IngredientAliases(Map<String, List<String>> table) {
        table.forEach((code, aliases) -> {
            if (code.startsWith("_")) {
                return;
            }
            for (String alias : aliases) {
                String n = normalize(alias);
                exact.put(n, code);
                if (n.length() >= MIN_CONTAINS_LENGTH) {
                    containsOrder.add(Map.entry(n, code));
                }
            }
            // 코드 다음 첫 한글 별칭을 V0 가짜 응답의 표시 이름으로 쓴다
            displayNames.put(code, aliases.size() > 1 ? aliases.get(1) : code);
        });
        containsOrder.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
    }

    @SuppressWarnings("unchecked")
    public static IngredientAliases load(ObjectMapper objectMapper) {
        try (InputStream in = IngredientAliases.class.getResourceAsStream("/eval/ingredient-aliases.json")) {
            Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {
            });
            Map<String, List<String>> table = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (v instanceof List<?> list) {
                    table.put(k, (List<String>) list);
                }
            });
            return new IngredientAliases(table);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toCode(String name) {
        if (name == null) {
            return null;
        }
        String n = normalize(name);
        String hit = exact.get(n);
        if (hit != null) {
            return hit;
        }
        for (Map.Entry<String, String> e : containsOrder) {
            if (n.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    public String displayName(String code) {
        return displayNames.getOrDefault(code, code);
    }

    static String normalize(String s) {
        return s.toLowerCase().replaceAll("[\\s\\-_().,'’·]", "");
    }
}
