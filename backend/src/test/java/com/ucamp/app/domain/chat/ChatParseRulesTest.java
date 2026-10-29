package com.ucamp.app.domain.chat;

import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.chat.parse.ChatParseValidator;
import com.ucamp.app.domain.chat.parse.ClarificationGuard;
import com.ucamp.app.domain.chat.parse.RedFlagDetector;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// ① 구조화 출력 검증·되묻기 질문 검증·응급 키워드 (설계 4-1, 4-2, §11 P7)
class ChatParseRulesTest {
    static final Set<String> CLASSES = Set.of("WARFARIN", "ANTICOAGULANT_UNKNOWN");
    static final Set<String> CONDITIONS = Set.of("HYPERTENSION", "DIABETES", "KIDNEY", "THYROID");
    final ObjectMapper om = new ObjectMapper();

    ObjectNode valid() {
        ObjectNode n = om.createObjectNode();
        n.put("red_flag", "NONE");
        n.put("off_topic", false);
        n.put("needs_clarification", false);
        n.putNull("clarification");
        n.put("search_query", "피로가 쌓이고 잠들기 어려운 사람에게 추천");
        n.putArray("medications").addObject().put("name", "와파린").put("drug_class", "WARFARIN");
        n.putArray("conditions").add("HYPERTENSION");
        return n;
    }

    @Test
    void 정상_출력은_통과() {
        ChatParseValidator.Result r = ChatParseValidator.validate(valid(), CLASSES, CONDITIONS);

        assertThat(r.errors()).isEmpty();
        assertThat(r.parse().medications()).containsExactly(new ChatParse.Medication("와파린", "WARFARIN"));
        assertThat(r.parse().conditions()).containsExactly("HYPERTENSION");
    }

    @Test
    void enum_밖_코드_80자_초과_검색문장은_실패_이유를_돌려준다() {
        ObjectNode n = valid();
        n.put("red_flag", "MAYBE");
        n.put("search_query", "가".repeat(81));
        n.putArray("medications").addObject().put("name", "쿠마딘").put("drug_class", "BLOOD_THINNER");
        n.putArray("conditions").add("CANCER");

        ChatParseValidator.Result r = ChatParseValidator.validate(n, CLASSES, CONDITIONS);

        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).hasSize(4);
    }

    @Test
    void 되묻기가_필요하면_질문과_선택지_2_4개() {
        ObjectNode n = valid();
        n.put("needs_clarification", true);
        n.put("search_query", "");
        assertThat(ChatParseValidator.validate(n, CLASSES, CONDITIONS).ok()).isFalse();

        ObjectNode c = n.putObject("clarification");
        c.put("question", "주로 언제 가장 힘드세요?");
        c.putArray("options").add("아침에 일어날 때").add("오후 업무 중");
        ChatParseValidator.Result r = ChatParseValidator.validate(n, CLASSES, CONDITIONS);
        assertThat(r.errors()).isEmpty();
        assertThat(r.parse().clarification().options()).hasSize(2);
    }

    @Test
    void 검색할_수_있는_입력인데_검색문장이_비면_실패() {
        ObjectNode n = valid();
        n.put("search_query", " ");

        assertThat(ChatParseValidator.validate(n, CLASSES, CONDITIONS).ok()).isFalse();
    }

    @Test
    void 되묻기_질문에_성분_제품_복용량이_있으면_고정_질문() {
        assertThat(ClarificationGuard.check(new ChatParse.Clarification("마그네슘을 드셔 보셨나요?", List.of("네", "아니요"))))
                .isEqualTo(ClarificationGuard.fixed());
        assertThat(ClarificationGuard.check(new ChatParse.Clarification("얼마나 피곤하세요?", List.of("500mg", "1000mg"))))
                .isEqualTo(ClarificationGuard.fixed());
        assertThat(ClarificationGuard.check(new ChatParse.Clarification("어떤 제품을 원하세요?", List.of("가", "나"))))
                .isEqualTo(ClarificationGuard.fixed());
        assertThat(ClarificationGuard.check(new ChatParse.Clarification("가".repeat(61), List.of("가", "나"))))
                .isEqualTo(ClarificationGuard.fixed());

        ChatParse.Clarification ok = new ChatParse.Clarification("주로 언제 가장 힘드세요?",
                List.of("아침에 일어날 때", "오후 업무 중", "잠들기 전"));
        assertThat(ClarificationGuard.check(ok)).isEqualTo(ok);
    }

    @Test
    void 검색_문장에_약_이름이나_약_계열_이름이_있으면_실패하고_찾은_단어를_돌려준다() {
        List<String> banned = List.of("와파린", "항응고제", "혈압약");
        ObjectNode n = valid();
        n.put("search_query", "와파린을 복용 중이며 피로를 느끼는 사람에게 추천");

        ChatParseValidator.Result r = ChatParseValidator.validate(n, CLASSES, CONDITIONS, banned);

        assertThat(r.ok()).isFalse();
        assertThat(r.onlyBannedTerms()).isTrue(); // 다른 오류는 없음 → 재요청 뒤 단어만 지우고 진행 가능
        assertThat(r.bannedFound()).containsExactly("와파린");

        n.put("search_query", "항응고 제를 먹는 사람에게 추천"); // 공백이 끼어도 찾는다
        assertThat(ChatParseValidator.validate(n, CLASSES, CONDITIONS, List.of("항응고제")).bannedFound())
                .containsExactly("항응고제");
        n.put("search_query", "피로가 쌓여 기운이 없는 사람에게 추천");
        assertThat(ChatParseValidator.validate(n, CLASSES, CONDITIONS, banned).ok()).isTrue();
    }

    @Test
    void 남은_약_이름은_그_단어만_지운다() {
        assertThat(ChatParseValidator.stripTerms("와파린 복용 중 피로가 쌓인 사람에게 추천", List.of("와파린")))
                .isEqualTo("복용 중 피로가 쌓인 사람에게 추천");
    }

    @Test
    void 응급_문구_기절_경련이_멈추지_않음은_걸리고_눈꺼풀_경련은_통과() {
        assertThat(RedFlagDetector.detect("친구가 기절했어요")).contains(ChatParse.EMERGENCY);
        assertThat(RedFlagDetector.detect("경련이 멈추지 않아요")).contains(ChatParse.EMERGENCY);
        assertThat(RedFlagDetector.detect("갑자기 의식을 잃었어요")).contains(ChatParse.EMERGENCY);
        assertThat(RedFlagDetector.detect("눈꺼풀 경련이 있어요")).isEmpty();
    }

    @Test
    void 응급_위기_키워드는_공백을_무시하고_위기가_먼저() {
        assertThat(RedFlagDetector.detect("가슴이 너무 아프고 숨이 차요")).contains(ChatParse.EMERGENCY);
        assertThat(RedFlagDetector.detect("숨이 안 쉬어져요")).contains(ChatParse.EMERGENCY);
        assertThat(RedFlagDetector.detect("요즘 죽고 싶고 가슴이 너무 아파요")).contains(ChatParse.CRISIS);
        // 영양제 상담에 흔한 표현은 걸리지 않는다
        assertThat(RedFlagDetector.detect("눈꺼풀 경련이 자주 있어요")).isEmpty();
        assertThat(RedFlagDetector.detect("남들 시선을 의식해서 잠을 못 자요")).isEmpty();
        assertThat(RedFlagDetector.detect("요즘 피곤해요")).isEmpty();
    }
}
