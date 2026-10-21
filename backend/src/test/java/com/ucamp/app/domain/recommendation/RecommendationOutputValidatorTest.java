package com.ucamp.app.domain.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationOutputValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationOutputValidatorTest {
    private final RecommendationOutputValidator validator = new RecommendationOutputValidator();
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void 정상() throws Exception {
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"VITAMIN_C","reason":"흡연","priority":1,"amount":125,"unit":"mg"},
                                    {"ingredient":"VITAMIN_D","reason":"햇빛","priority":2,"amount":null,"unit":null}],
                 "summary":"요약"}"""), true)).isEmpty();
        assertThat(validator.validate(json("{\"recommendations\":[],\"summary\":\"없음\"}"), false)).isEmpty();
    }

    @Test
    void 도구를_호출하지_않음() {
        assertThat(validator.validate(null, true)).containsExactly("submit_recommendation 도구를 호출하지 않았다");
    }

    @Test
    void 알_수_없는_성분_코드() throws Exception {
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"VITAMIN_E","reason":"x","priority":1}],"summary":"s"}"""), false))
                .anyMatch(e -> e.contains("VITAMIN_E"));
    }

    @Test
    void 중복_성분() throws Exception {
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"IRON","reason":"x","priority":1},
                                    {"ingredient":"IRON","reason":"y","priority":2}],"summary":"s"}"""), false))
                .anyMatch(e -> e.contains("중복"));
    }

    @Test
    void priority_불연속() throws Exception {
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"IRON","reason":"x","priority":1},
                                    {"ingredient":"ZINC","reason":"y","priority":3}],"summary":"s"}"""), false))
                .anyMatch(e -> e.contains("연속"));
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"IRON","reason":"x","priority":2}],"summary":"s"}"""), false))
                .anyMatch(e -> e.contains("연속"));
    }

    @Test
    void 스키마_위반_필드_누락과_타입() throws Exception {
        assertThat(validator.validate(json("{\"recommendations\":[{\"ingredient\":\"IRON\"}]}"), false))
                .anyMatch(e -> e.contains("summary"))
                .anyMatch(e -> e.contains("reason"))
                .anyMatch(e -> e.contains("priority"));
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"IRON","reason":"x","priority":1,"amount":"8","unit":"mg"}],"summary":"s"}"""), true))
                .anyMatch(e -> e.contains("amount"));
        assertThat(validator.validate(json("""
                {"recommendations":[{"ingredient":"IRON","reason":"x","priority":1,"amount":8,"unit":null}],"summary":"s"}"""), true))
                .anyMatch(e -> e.contains("함께"));
    }
}
