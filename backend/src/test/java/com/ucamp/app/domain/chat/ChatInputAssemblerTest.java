package com.ucamp.app.domain.chat;

import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.chat.parse.DrugCatalog;
import com.ucamp.app.domain.chat.service.ChatInputAssembler;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput.Medication;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 대화 속 약 매칭 (설계 4-3): ① 이름 일치(공백 제거) → ② LLM 계열 ("이름 모름" 행 우선) → ③ 모름
class ChatInputAssemblerTest {
    // seed-drugs.sql과 같은 모양 (일부)
    static final List<DrugCatalog.Entry> DRUGS = List.of(
            new DrugCatalog.Entry("D-007", "와파린", "WARFARIN"),
            new DrugCatalog.Entry("D-013", "클로피도그렐", "ANTIPLATELET"),
            new DrugCatalog.Entry("D-U05", "항응고제 (이름 모름)", "ANTICOAGULANT_UNKNOWN"),
            new DrugCatalog.Entry("D-U09", "항혈소판제 (이름 모름)", "ANTIPLATELET"));

    final ChatInputAssembler assembler = new ChatInputAssembler(null, new DrugCatalog(null), new ObjectMapper());

    static ChatParse parse(ChatParse.Medication... meds) {
        return new ChatParse("NONE", false, false, null, "q", List.of(meds), List.of("HYPERTENSION"));
    }

    @Test
    void 이름이_같으면_그_약_공백은_무시() {
        ChatInputAssembler.Extracted e = assembler.match(parse(new ChatParse.Medication("와 파린", "UNKNOWN")), DRUGS);

        assertThat(e.medications()).containsExactly(new Medication("D-007", "WARFARIN", "와파린"));
        assertThat(e.hasUnmatched()).isFalse();
        assertThat(e.conditions()).containsExactly("HYPERTENSION");
    }

    @Test
    void 이름이_없으면_계열의_이름_모름_행이_먼저() {
        ChatInputAssembler.Extracted e = assembler.match(parse(
                new ChatParse.Medication("피 묽게 하는 약", "ANTICOAGULANT_UNKNOWN"),
                new ChatParse.Medication("혈전 예방약", "ANTIPLATELET")), DRUGS);

        assertThat(e.medications()).extracting(Medication::drugId).containsExactly("D-U05", "D-U09");
    }

    @Test
    void 이름_아는_약의_계열을_골랐는데_이름이_다르면_그_계열_행() {
        ChatInputAssembler.Extracted e = assembler.match(parse(new ChatParse.Medication("쿠마딘", "WARFARIN")), DRUGS);

        assertThat(e.medications()).containsExactly(new Medication("D-007", "WARFARIN", "와파린"));
    }

    @Test
    void 모르면_이름만_있는_약으로_넣고_안내_대상() {
        ChatInputAssembler.Extracted e = assembler.match(parse(new ChatParse.Medication("이름모를약", "UNKNOWN")), DRUGS);

        assertThat(e.medications()).containsExactly(new Medication(null, null, "이름모를약"));
        assertThat(e.unmatched()).containsExactly("이름모를약");
    }
}
