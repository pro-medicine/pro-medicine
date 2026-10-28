package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator.Code;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator.Violation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 설계 6-4: 검사 항목마다 실패 케이스 하나씩
class PersonalizedQuestionValidatorTest {
    private final ObjectMapper om = new ObjectMapper();
    private final PersonalizedQuestionValidator validator = new PersonalizedQuestionValidator();

    @Test
    void 올바른_질문은_통과() {
        assertThat(validate(question("피곤함은 주로 언제 느끼세요?", "SINGLE",
                "아침에 피곤해요", "CONCERN", "하루 종일 피곤해요", "CONCERN", "없어요", "NONE", "잘 모르겠어요", "UNKNOWN"),
                "SINGLE")).isEmpty();
    }

    @Test
    void JSON_형식이_아니면_FORMAT() {
        assertThat(codes(validator.validate(null, "SINGLE", List.of()))).containsExactly(Code.FORMAT);
        assertThat(codes(validator.validate(om.createObjectNode().put("question", "q"), "SINGLE", List.of())))
                .containsExactly(Code.FORMAT);
        ObjectNode badKind = question("질문?", "SINGLE", "a", "MAYBE", "잘 모르겠어요", "UNKNOWN");
        assertThat(codes(validate(badKind, "SINGLE"))).contains(Code.FORMAT);
    }

    @Test
    void 요청한_answerType과_다르면_실패() {
        assertThat(codes(validate(ok("MULTI"), "SINGLE"))).containsExactly(Code.ANSWER_TYPE);
    }

    @Test
    void 선택지는_2개에서_5개() {
        assertThat(codes(validate(question("질문?", "SINGLE", "잘 모르겠어요", "UNKNOWN"), "SINGLE")))
                .containsExactly(Code.OPTION_COUNT);
        assertThat(codes(validate(question("질문?", "SINGLE", "a", "NORMAL", "b", "NORMAL", "c", "NORMAL",
                "d", "CONCERN", "e", "NONE", "잘 모르겠어요", "UNKNOWN"), "SINGLE"))).containsExactly(Code.OPTION_COUNT);
    }

    @Test
    void 라벨_중복과_길이() {
        assertThat(codes(validate(question("질문?", "SINGLE", "그래요", "NORMAL", "그래요 ", "CONCERN",
                "잘 모르겠어요", "UNKNOWN"), "SINGLE"))).containsExactly(Code.DUPLICATE_LABEL);
        assertThat(codes(validate(question("질문?", "SINGLE", "가".repeat(21), "NORMAL", "잘 모르겠어요", "UNKNOWN"),
                "SINGLE"))).containsExactly(Code.LABEL_LENGTH);
        assertThat(codes(validate(question("질문?", "SINGLE", " ", "NORMAL", "잘 모르겠어요", "UNKNOWN"),
                "SINGLE"))).containsExactly(Code.LABEL_LENGTH);
    }

    @Test
    void UNKNOWN은_정확히_1개() {
        assertThat(codes(validate(question("질문?", "SINGLE", "a", "NORMAL", "b", "CONCERN"), "SINGLE")))
                .containsExactly(Code.UNKNOWN_COUNT);
        assertThat(codes(validate(question("질문?", "SINGLE", "a", "UNKNOWN", "b", "UNKNOWN"), "SINGLE")))
                .containsExactly(Code.UNKNOWN_COUNT);
    }

    @Test
    void NONE은_1개_이하() {
        assertThat(codes(validate(question("질문?", "SINGLE", "없어요", "NONE", "전혀 없어요", "NONE",
                "잘 모르겠어요", "UNKNOWN"), "SINGLE"))).containsExactly(Code.NONE_COUNT);
    }

    @Test
    void 질문은_1자에서_60자() {
        assertThat(codes(validate(question(" ", "SINGLE", "a", "NORMAL", "잘 모르겠어요", "UNKNOWN"), "SINGLE")))
                .containsExactly(Code.QUESTION_LENGTH);
        assertThat(codes(validate(question("가".repeat(61), "SINGLE", "a", "NORMAL", "잘 모르겠어요", "UNKNOWN"),
                "SINGLE"))).containsExactly(Code.QUESTION_LENGTH);
    }

    @Test
    void 이전_질문과_같은_문장이면_실패_공백_차이는_같은_문장() {
        List<Violation> v = validator.validate(question("피곤함은 언제 느끼세요?", "SINGLE", "a", "NORMAL",
                "잘 모르겠어요", "UNKNOWN"), "SINGLE", List.of("피곤함은  언제 느끼세요?"));
        assertThat(codes(v)).containsExactly(Code.REPEATED_QUESTION);
    }

    @Test
    void 금지어는_질문과_선택지_모두_막는다() {
        for (String word : PersonalizedQuestionValidator.BANNED_WORDS) {
            assertThat(codes(validate(question("약을 " + word + "해야 할까요?", "SINGLE", "a", "NORMAL",
                    "잘 모르겠어요", "UNKNOWN"), "SINGLE"))).as(word).containsExactly(Code.BANNED_WORD);
        }
        assertThat(codes(validate(question("요즘 어떠세요?", "SINGLE", "약을 끊으세요", "NORMAL",
                "잘 모르겠어요", "UNKNOWN"), "SINGLE"))).containsExactly(Code.BANNED_WORD);
    }

    private List<Violation> validate(JsonNode output, String expected) {
        return validator.validate(output, expected, List.of());
    }

    private ObjectNode ok(String answerType) {
        return question("요즘 어떠세요?", answerType, "괜찮아요", "NORMAL", "잘 모르겠어요", "UNKNOWN");
    }

    private ObjectNode question(String text, String answerType, String... labelKind) {
        ObjectNode root = om.createObjectNode();
        root.put("question", text);
        root.put("answerType", answerType);
        ArrayNode options = root.putArray("options");
        for (int i = 0; i < labelKind.length; i += 2) {
            options.addObject().put("label", labelKind[i]).put("kind", labelKind[i + 1]);
        }
        return root;
    }

    private static List<Code> codes(List<Violation> v) {
        return v.stream().map(Violation::code).toList();
    }
}
