package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.planner.OptionKind;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 맞춤 질문 구조화 출력 검증 (설계 6-4). 실패 이유(message)는 재요청 메시지에 그대로 붙이고,
 * 종류(code)는 측정에서 "어떤 규칙이 얼마나 막았는지" 집계에 쓴다.
 */
@Component
public class PersonalizedQuestionValidator {
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 5;
    public static final int MAX_LABEL_LENGTH = 20;
    public static final int MAX_QUESTION_LENGTH = 60;
    /** 약을 끊게 하거나 진단·처방처럼 들리는 말 */
    public static final List<String> BANNED_WORDS = List.of("중단", "끊으세요", "진단", "처방", "복용을 멈추");

    /** 검증 실패 종류 */
    public enum Code {
        FORMAT, ANSWER_TYPE, OPTION_COUNT, DUPLICATE_LABEL, LABEL_LENGTH, UNKNOWN_COUNT, NONE_COUNT,
        QUESTION_LENGTH, REPEATED_QUESTION, BANNED_WORD
    }

    public record Violation(Code code, String message) {
    }

    /**
     * @param expectedAnswerType 서버가 요청한 SINGLE / MULTI
     * @param previousQuestions  이 회차에서 이미 낸 질문 문장
     */
    public List<Violation> validate(JsonNode output, String expectedAnswerType, Collection<String> previousQuestions) {
        List<Violation> v = new ArrayList<>();
        if (output == null || output.isNull() || output.isMissingNode() || !output.isObject()) {
            v.add(new Violation(Code.FORMAT, "정해진 JSON 형식(submit_question 스키마)으로 답하지 않았다"));
            return v;
        }
        JsonNode question = output.get("question");
        JsonNode answerType = output.get("answerType");
        JsonNode options = output.get("options");
        if (question == null || !question.isString() || answerType == null || !answerType.isString()
                || options == null || !options.isArray()) {
            v.add(new Violation(Code.FORMAT, "question(문자열), answerType(문자열), options(배열)가 모두 있어야 한다"));
            return v;
        }
        if (!expectedAnswerType.equals(answerType.asString())) {
            v.add(new Violation(Code.ANSWER_TYPE, "answerType은 " + expectedAnswerType + "이어야 한다 (받은 값: "
                    + answerType.asString() + ")"));
        }
        validateQuestion(question.asString(), previousQuestions, v);
        validateOptions(options, v);
        return v;
    }

    private static void validateQuestion(String text, Collection<String> previous, List<Violation> v) {
        String q = text.strip();
        if (q.isEmpty() || q.length() > MAX_QUESTION_LENGTH) {
            v.add(new Violation(Code.QUESTION_LENGTH, "질문은 1~" + MAX_QUESTION_LENGTH + "자여야 한다 (지금 " + q.length() + "자)"));
        }
        String normalized = normalize(q);
        if (previous.stream().anyMatch(p -> normalize(p).equals(normalized))) {
            v.add(new Violation(Code.REPEATED_QUESTION, "이전 질문과 같은 문장이다. 다른 내용을 물어라"));
        }
        bannedWord(q).ifPresent(w -> v.add(new Violation(Code.BANNED_WORD, "질문에 쓰면 안 되는 말 '" + w + "'이 있다")));
    }

    private static void validateOptions(JsonNode options, List<Violation> v) {
        int count = options.size();
        if (count < MIN_OPTIONS || count > MAX_OPTIONS) {
            v.add(new Violation(Code.OPTION_COUNT, "선택지는 " + MIN_OPTIONS + "~" + MAX_OPTIONS + "개여야 한다 (지금 " + count + "개)"));
        }
        Set<String> labels = new HashSet<>();
        int unknown = 0;
        int none = 0;
        int index = 0;
        for (JsonNode o : options) {
            index++;
            String at = "options[" + index + "]";
            JsonNode label = o.get("label");
            JsonNode kind = o.get("kind");
            if (label == null || !label.isString() || kind == null || !kind.isString() || kindOf(kind.asString()) == null) {
                v.add(new Violation(Code.FORMAT, at + "에 label(문자열)과 kind(NONE/UNKNOWN/NORMAL/CONCERN)가 있어야 한다"));
                continue;
            }
            String l = label.asString().strip();
            if (l.isEmpty() || l.length() > MAX_LABEL_LENGTH) {
                v.add(new Violation(Code.LABEL_LENGTH, at + ".label은 1~" + MAX_LABEL_LENGTH + "자여야 한다 (지금 " + l.length() + "자)"));
            }
            if (!labels.add(normalize(l))) {
                v.add(new Violation(Code.DUPLICATE_LABEL, at + ".label '" + l + "'이 다른 선택지와 같다"));
            }
            bannedWord(l).ifPresent(w -> v.add(new Violation(Code.BANNED_WORD, at + ".label에 쓰면 안 되는 말 '" + w + "'이 있다")));
            OptionKind k = kindOf(kind.asString());
            if (k == OptionKind.UNKNOWN) {
                unknown++;
            } else if (k == OptionKind.NONE) {
                none++;
            }
        }
        if (unknown != 1) {
            v.add(new Violation(Code.UNKNOWN_COUNT, "kind가 UNKNOWN인 선택지(\"잘 모르겠어요\")가 정확히 1개여야 한다 (지금 " + unknown + "개)"));
        }
        if (none > 1) {
            v.add(new Violation(Code.NONE_COUNT, "kind가 NONE인 선택지(\"없어요\")는 1개 이하여야 한다 (지금 " + none + "개)"));
        }
    }

    public static Optional<String> bannedWord(String text) {
        return BANNED_WORDS.stream().filter(text::contains).findFirst();
    }

    private static OptionKind kindOf(String value) {
        try {
            return OptionKind.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").strip();
    }
}
