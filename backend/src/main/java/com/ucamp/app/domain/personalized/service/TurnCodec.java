package com.ucamp.app.domain.personalized.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

// ai_question_turns의 JSON 컬럼(options_json, selected_json) ↔ 객체
@Component
@RequiredArgsConstructor
public class TurnCodec {
    private final ObjectMapper objectMapper;

    public String encodeOptions(List<TurnOption> options) {
        return objectMapper.writeValueAsString(options);
    }

    public List<TurnOption> decodeOptions(String json) {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    public String encodeSelected(List<Integer> selected) {
        return objectMapper.writeValueAsString(selected);
    }

    public List<Integer> decodeSelected(String json) {
        if (json == null) {
            return null;
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }
}
