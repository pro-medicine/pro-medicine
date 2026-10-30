package com.ucamp.app.domain.chat.service;

import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.chat.parse.DrugCatalog;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * V4 안전 입력 조립 (설계 4-3). assembleForMember(프로필 + 임신 답) 결과에 대화 속 약·질환을 toBuilder()로 합친다.
 * 트랙 1·트랙 2·V3 제품 API(RecommendationConditionLoader)가 모두 이 클래스로만 만든다.
 * 대화에서 뽑은 값은 프로필에 저장하지 않고 ai_chat_sessions.extra_medications·extra_conditions에만 둔다
 */
@Component
@RequiredArgsConstructor
public class ChatInputAssembler {
    private static final TypeReference<List<RecommendationInput.Medication>> MEDICATIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final RecommendationInputAssembler assembler;
    private final DrugCatalog drugCatalog;
    private final ObjectMapper objectMapper;

    /**
     * 대화 속 약 매칭 결과
     *
     * @param unmatched 약 마스터로 찾지 못한 약 이름 (있으면 "확인하지 못한 약" 안내)
     */
    public record Extracted(List<RecommendationInput.Medication> medications, List<String> conditions,
                            List<String> unmatched) {
        public static final Extracted NONE = new Extracted(List.of(), List.of(), List.of());

        public boolean hasUnmatched() {
            return !unmatched.isEmpty();
        }
    }

    /**
     * 약 매칭 순서: ① 이름 일치(공백 제거) → ② LLM drug_class의 행 ("이름 모름" 행 우선, 없으면 그 계열 행)
     * → ③ UNKNOWN이면 Medication(null, null, name) ("복용 약 있음"으로 보수적으로 처리)
     */
    public Extracted match(ChatParse parse, List<DrugCatalog.Entry> drugs) {
        List<RecommendationInput.Medication> meds = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (ChatParse.Medication m : parse.medications()) {
            Optional<DrugCatalog.Entry> hit = drugCatalog.byName(drugs, m.name())
                    .or(() -> drugCatalog.byClass(drugs, m.drugClass()));
            RecommendationInput.Medication med = hit
                    .map(e -> new RecommendationInput.Medication(e.drugId(), e.drugClass(), e.name()))
                    .orElseGet(() -> new RecommendationInput.Medication(null, null, m.name()));
            if (hit.isEmpty()) {
                unmatched.add(m.name());
            }
            if (!meds.contains(med)) {
                meds.add(med);
            }
        }
        return new Extracted(List.copyOf(meds), List.copyOf(parse.conditions()), List.copyOf(unmatched));
    }

    /** 프로필 + 대화 속 약·질환 (트랙 1 안전 판단·트랙 2 입력의 기본) */
    public RecommendationInput assemble(Long userId, String pregnancy, Extracted extracted) {
        RecommendationInput base = assembler.assembleForMember(userId, pregnancy);
        return merge(base, extracted.medications(), extracted.conditions());
    }

    /** 세션에 저장한 값으로 다시 만든다 (트랙 2 결과를 V3 제품 API로 볼 때) */
    public RecommendationInput forSession(AiChatSession session) {
        RecommendationInput base = assembler.assembleForMember(session.getUser().getId(), session.getPregnancy());
        return merge(base, readMedications(session.getExtraMedications()), readStrings(session.getExtraConditions()));
    }

    public String writeMedications(List<RecommendationInput.Medication> medications) {
        return objectMapper.writeValueAsString(medications);
    }

    public String writeConditions(List<String> conditions) {
        return objectMapper.writeValueAsString(conditions);
    }

    static RecommendationInput merge(RecommendationInput base, List<RecommendationInput.Medication> extraMeds,
                                     List<String> extraConditions) {
        List<RecommendationInput.Medication> meds = new ArrayList<>(base.medicationsOrEmpty());
        for (RecommendationInput.Medication m : extraMeds) {
            boolean dup = meds.stream().anyMatch(p -> sameDrug(p, m));
            if (!dup) {
                meds.add(m);
            }
        }
        LinkedHashSet<String> conditions = new LinkedHashSet<>(base.conditionsOrEmpty());
        conditions.addAll(extraConditions);
        return base.toBuilder()
                .medications(List.copyOf(meds))
                .conditions(List.copyOf(conditions))
                .build();
    }

    private static boolean sameDrug(RecommendationInput.Medication a, RecommendationInput.Medication b) {
        if (a.drugId() != null || b.drugId() != null) {
            return Objects.equals(a.drugId(), b.drugId());
        }
        return a.name() != null && b.name() != null
                && a.name().replaceAll("\\s+", "").equals(b.name().replaceAll("\\s+", ""));
    }

    private List<RecommendationInput.Medication> readMedications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, MEDICATIONS);
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, STRINGS);
    }
}
