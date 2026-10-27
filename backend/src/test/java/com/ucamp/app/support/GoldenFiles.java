package com.ucamp.app.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 고정(골든) 테스트 도우미. 리팩터링 전후로 LLM 요청·결과·응답이 한 글자도 바뀌지 않았는지 비교한다.
 * 기대값은 src/test/resources/golden/{name}.json. 의도적으로 바꿀 때만 환경변수 GOLDEN_UPDATE=true로 다시 만든다.
 */
public final class GoldenFiles {
    private static final Path DIR = Path.of("src", "test", "resources", "golden");
    private static final Path ACTUAL_DIR = Path.of("build", "golden-actual");
    private static final ObjectMapper PRETTY = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private GoldenFiles() {
    }

    public static void assertMatches(String name, Object snapshot) {
        String actual = normalize(PRETTY.writeValueAsString(sortKeys(PRETTY.valueToTree(snapshot))));
        Path file = DIR.resolve(name + ".json");
        try {
            if ("true".equalsIgnoreCase(System.getenv("GOLDEN_UPDATE")) || !Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                return;
            }
            String expected = normalize(Files.readString(file, StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                // 비교하기 쉽게 실제 값을 build/golden-actual/ 에 남긴다
                Path out = ACTUAL_DIR.resolve(name + ".json");
                Files.createDirectories(out.getParent());
                Files.writeString(out, actual, StandardCharsets.UTF_8);
            }
            assertThat(actual).as("골든 파일 %s와 다르다", file).isEqualTo(expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // JSON 객체는 키 순서와 무관하다 (Map.of 순서는 JVM 실행마다 달라진다). 배열 순서는 그대로 비교한다
    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = PRETTY.createObjectNode();
            new TreeSet<>(node.propertyNames()).forEach(k -> sorted.set(k, sortKeys(node.get(k))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arr = PRETTY.createArrayNode();
            node.forEach(n -> arr.add(sortKeys(n)));
            return arr;
        }
        return node;
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").strip() + "\n";
    }
}
