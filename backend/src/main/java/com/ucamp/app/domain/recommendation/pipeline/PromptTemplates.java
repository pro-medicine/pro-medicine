package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

// src/main/resources/prompts/{버전}/ 아래 템플릿 파일을 읽어 캐시한다. {{key}}를 값으로 바꾼다
@Component
public class PromptTemplates {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String path) {
        return cache.computeIfAbsent(path, p -> {
            try {
                return new ClassPathResource("prompts/" + p).getContentAsString(StandardCharsets.UTF_8)
                        .replace("\r\n", "\n").strip();
            } catch (IOException e) {
                throw new UncheckedIOException("prompt template not found: " + p, e);
            }
        });
    }

    public String render(String path, Map<String, String> values) {
        String text = load(path);
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return text;
    }

    /** 프롬프트에 넣는 "- 항목" 목록 (한 줄에 하나) */
    public static String bulletList(Collection<String> lines) {
        return lines.stream().map(l -> "- " + l).collect(Collectors.joining("\n"));
    }
}
