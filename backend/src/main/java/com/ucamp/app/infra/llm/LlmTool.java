package com.ucamp.app.infra.llm;

import java.util.Map;

// tool use 도구 정의. inputSchema는 JSON Schema. strict=true면 API가 입력을 스키마대로 강제
public record LlmTool(String name, String description, Map<String, Object> inputSchema, boolean strict) {
}
