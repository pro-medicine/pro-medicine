package com.ucamp.app.infra.llm;

import java.util.Map;

/**
 * 구조화 출력에 쓸 JSON Schema. strict=true면 API가 생성 단계에서 스키마를 강제한다.
 * name은 코드·로그용 식별자다 (OpenAI 요청에는 스키마 본문과 strict만 들어간다).
 */
public record LlmOutputSchema(String name, Map<String, Object> schema, boolean strict) {
}
