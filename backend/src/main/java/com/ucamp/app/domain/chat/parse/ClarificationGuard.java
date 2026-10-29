package com.ucamp.app.domain.chat.parse;

import com.ucamp.app.domain.health.entity.IngredientCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 되묻기 질문 검증 (설계 §11 P7). 화면에 나가는 LLM 문장은 되묻기 질문뿐이라, 길이를 넘거나 성분·제품·복용량 표현이 있으면
 * 질문과 선택지를 모두 서버 고정 질문으로 바꾼다 (LLM이 추천을 흘리지 못하게)
 */
public final class ClarificationGuard {
    public static final String FIXED_QUESTION = "어떤 점이 가장 불편하신지 조금 더 알려주세요";
    public static final List<String> FIXED_OPTIONS = List.of(
            "피곤하고 기운이 없어요", "잠을 잘 못 자요", "소화가 잘 안 돼요", "뼈·관절이 걱정돼요");

    // 복용량 (숫자 + 단위), 복용·제품 관련 말. "드세요"처럼 짧은 말은 "힘드세요"에도 걸려서 넣지 않는다
    private static final Pattern DOSE = Pattern.compile(
            "\\d+(\\.\\d+)?\\s*(mg|mcg|μg|㎍|iu|g|정|알|캡슐|포|억|cfu)", Pattern.CASE_INSENSITIVE);
    private static final List<String> BANNED_WORDS = List.of(
            "복용량", "용량", "복용하", "섭취하", "제품", "브랜드", "영양제를", "추천");
    private static final List<String> INGREDIENT_WORDS = ingredientWords();

    private ClarificationGuard() {
    }

    public static ChatParse.Clarification fixed() {
        return new ChatParse.Clarification(FIXED_QUESTION, FIXED_OPTIONS);
    }

    /** 통과하면 그대로, 아니면 고정 질문 */
    public static ChatParse.Clarification check(ChatParse.Clarification c) {
        if (c == null || c.question() == null || c.options() == null) {
            return fixed();
        }
        if (c.question().length() > ChatParseValidator.QUESTION_MAX
                || c.options().size() < ChatParseValidator.OPTIONS_MIN
                || c.options().size() > ChatParseValidator.OPTIONS_MAX
                || c.options().stream().anyMatch(o -> o == null || o.isBlank() || o.length() > ChatParseValidator.OPTION_MAX)) {
            return fixed();
        }
        List<String> texts = new ArrayList<>(c.options());
        texts.add(c.question());
        if (texts.stream().anyMatch(ClarificationGuard::banned)) {
            return fixed();
        }
        return c;
    }

    static boolean banned(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("\\s+", "");
        if (DOSE.matcher(text).find()) {
            return true;
        }
        if (BANNED_WORDS.stream().anyMatch(compact::contains)) {
            return true;
        }
        return INGREDIENT_WORDS.stream().anyMatch(compact::contains);
    }

    // 성분 한글 이름(괄호 안 별칭 포함)과 영문 코드. "비타민 D (…)" → "비타민d"
    private static List<String> ingredientWords() {
        List<String> out = new ArrayList<>();
        for (IngredientCode code : IngredientCode.values()) {
            out.add(code.name().toLowerCase(Locale.ROOT));
            String name = code.getNameKo().toLowerCase(Locale.ROOT);
            int paren = name.indexOf('(');
            String main = (paren < 0 ? name : name.substring(0, paren)).replaceAll("\\s+", "");
            if (!main.isEmpty()) {
                out.add(main);
            }
            if (paren >= 0 && name.indexOf(')') > paren) {
                for (String alias : name.substring(paren + 1, name.indexOf(')')).split("[·,]")) {
                    String a = alias.replaceAll("\\s+", "");
                    if (a.length() >= 2) {
                        out.add(a);
                    }
                }
            }
        }
        out.addAll(List.of("오메가3", "유산균", "비타민", "종합비타민", "멀티비타민"));
        return List.copyOf(out);
    }
}
