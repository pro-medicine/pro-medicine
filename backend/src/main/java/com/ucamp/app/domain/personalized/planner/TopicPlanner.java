package com.ucamp.app.domain.personalized.planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 서버 주제 계획 (설계 5장). LLM을 부르지 않는 순수 계산이라 같은 프로필·같은 답이면 항상 같은 다음 주제가 나온다.
 * 회차에 저장된 질문 기록(history)만으로 다시 계산하므로 상태를 따로 들고 있지 않는다.
 *
 * <pre>
 * 순서: CONCERN_OPEN → MED:* (규칙 있는 계열 먼저) → CONCERN:* (고른 순서) → CONDITION:* → LIFESTYLE:*
 * 고른 답에 CONCERN이 있고 깊이가 남았으면 같은 주제 depth+1, 아니면 다음 주제
 * 새 주제는 asked < 5, 같은 주제 깊이 질문은 asked < 7일 때만. 못 맞추면 끝(READY)
 * </pre>
 * 고정 질문(임신·수유)은 계획 밖이며 history에 넣지 않는다.
 */
public class TopicPlanner {
    public static final int BASE_QUESTIONS = 5;
    public static final int MAX_QUESTIONS = 7;
    /** 불편한 점에서 이어서 물을 선택지 수 (MULTI 최대 선택 수와 같다) */
    public static final int MAX_CONCERN_TOPICS = 2;

    /** 주제 계획에 쓰는 프로필 값 (설문 value_code 그대로) */
    public record Profile(List<Medication> medications, Set<String> ruledDrugClasses, List<String> conditions,
                          String smoking, String drinking, String caffeine, String dietType, String sunExposure) {
        public Profile {
            medications = medications == null ? List.of() : medications;
            ruledDrugClasses = ruledDrugClasses == null ? Set.of() : ruledDrugClasses;
            conditions = conditions == null ? List.of() : conditions;
        }
    }

    /** drugClass가 없으면 약 마스터에 없는 약 (이름만) */
    public record Medication(String drugClass, String name) {
    }

    /** 이미 낸 LLM 질문 하나와 고른 답 (selected는 사용자가 고른 순서) */
    public record AskedTurn(String topicCode, int depth, List<SelectedOption> selected) {
    }

    public record SelectedOption(String label, OptionKind kind) {
    }

    /** 다음에 낼 질문의 주제와 깊이 */
    public record Next(Topic topic, int depth) {
    }

    /** 다음 질문. 비어 있으면 끝 (READY) */
    public Optional<Next> next(Profile profile, List<AskedTurn> history) {
        int asked = history.size();
        if (asked == 0) {
            return Optional.of(new Next(Topic.CONCERN_OPEN, 1));
        }
        if (asked >= MAX_QUESTIONS) {
            return Optional.empty();
        }
        Map<String, Topic> topics = topics(profile, history);
        AskedTurn last = history.get(asked - 1);
        Topic current = topics.get(last.topicCode());
        boolean concern = last.selected().stream().anyMatch(s -> s.kind() == OptionKind.CONCERN);
        if (current != null && concern && last.depth() < current.maxDepth()) {
            return Optional.of(new Next(current, last.depth() + 1));
        }
        if (asked >= BASE_QUESTIONS) {
            return Optional.empty();
        }
        Set<String> started = new HashSet<>();
        history.forEach(t -> started.add(t.topicCode()));
        return topics.values().stream()
                .filter(t -> !started.contains(t.code()))
                .findFirst()
                .map(t -> new Next(t, 1));
    }

    /** 이 프로필·답 기준의 전체 주제 목록 (순서대로, 같은 코드는 한 번만) */
    public List<Topic> plan(Profile profile, List<AskedTurn> history) {
        return List.copyOf(topics(profile, history).values());
    }

    private static Map<String, Topic> topics(Profile profile, List<AskedTurn> history) {
        Map<String, Topic> topics = new LinkedHashMap<>();
        add(topics, Topic.CONCERN_OPEN);
        medications(profile).forEach(t -> add(topics, t));
        concerns(history).forEach(t -> add(topics, t));
        profile.conditions().forEach(c -> add(topics, Topic.condition(c)));
        lifestyles(profile).forEach(t -> add(topics, t));
        return topics;
    }

    private static void add(Map<String, Topic> topics, Topic topic) {
        topics.putIfAbsent(topic.code(), topic);
    }

    // 규칙(recommendation_rules)이 있는 계열을 먼저, 그 안에서는 프로필 순서 그대로
    private static List<Topic> medications(Profile profile) {
        List<Topic> ruled = new ArrayList<>();
        List<Topic> others = new ArrayList<>();
        for (Medication m : profile.medications()) {
            if (m.drugClass() == null && (m.name() == null || m.name().isBlank())) {
                continue;
            }
            Topic t = Topic.medication(m.drugClass(), m.name());
            (m.drugClass() != null && profile.ruledDrugClasses().contains(m.drugClass()) ? ruled : others).add(t);
        }
        ruled.addAll(others);
        return ruled;
    }

    // 불편한 점(CONCERN_OPEN)에서 고른 선택지마다 주제 하나 (NONE·UNKNOWN 제외, 고른 순서, 최대 2개)
    private static List<Topic> concerns(List<AskedTurn> history) {
        return history.stream()
                .filter(t -> Topic.CONCERN_OPEN.code().equals(t.topicCode()))
                .findFirst()
                .map(t -> t.selected().stream()
                        .filter(s -> !s.kind().isExclusive())
                        .limit(MAX_CONCERN_TOPICS)
                        .map(s -> Topic.concern(s.label()))
                        .toList())
                .orElse(List.of());
    }

    private static List<Topic> lifestyles(Profile p) {
        List<Topic> topics = new ArrayList<>();
        if (p.smoking() != null && !"NONE".equals(p.smoking())) {
            topics.add(Topic.lifestyle("SMOKING"));
        }
        if ("HIGH".equals(p.drinking())) {
            topics.add(Topic.lifestyle("DRINKING"));
        }
        if ("HIGH".equals(p.caffeine())) {
            topics.add(Topic.lifestyle("CAFFEINE"));
        }
        if ("VEGAN".equals(p.dietType()) || "VEGETARIAN".equals(p.dietType())) {
            topics.add(Topic.lifestyle("DIET"));
        }
        if ("LOW".equals(p.sunExposure())) {
            topics.add(Topic.lifestyle("SUN"));
        }
        return topics;
    }
}
