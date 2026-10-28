package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.planner.TopicPlanner;

import java.util.List;
import java.util.Objects;

/**
 * 트랜잭션 밖(LLM 호출 중)에서 쓰는 회차 스냅샷. 엔티티를 트랜잭션 밖으로 넘기지 않기 위함.
 */
public record SessionState(Long sessionId, Long userId, String status, String pregnancy, int questionCount,
                           boolean stoppedByUser, Long recommendationId, List<TurnState> turns) {

    /** selected = 고른 index (답하기 전 null) */
    public record TurnState(Long turnId, int turnNo, boolean fixed, String topic, int depth, String question,
                            String answerType, List<TurnOption> options, List<Integer> selected) {
        public boolean answered() {
            return selected != null;
        }

        public List<TurnOption> selectedOptions() {
            return selected == null ? List.of() : selected.stream().map(options::get).toList();
        }
    }

    /** 지금 답을 기다리는 질문 (진행 중이고 마지막 질문에 아직 답하지 않았을 때) */
    public TurnState pendingTurn() {
        if (turns.isEmpty()) {
            return null;
        }
        TurnState last = turns.get(turns.size() - 1);
        return last.answered() ? null : last;
    }

    /** 답한 LLM 질문 (고정 질문 제외) */
    public List<TurnState> answeredLlmTurns() {
        return turns.stream().filter(t -> !t.fixed() && t.answered()).toList();
    }

    /** 주제 계획용 기록 */
    public List<TopicPlanner.AskedTurn> plannerHistory() {
        return answeredLlmTurns().stream()
                .map(t -> new TopicPlanner.AskedTurn(t.topic(), t.depth(), t.selectedOptions().stream()
                        .map(o -> new TopicPlanner.SelectedOption(o.label(), o.kind())).toList()))
                .toList();
    }

    public boolean is(String s) {
        return Objects.equals(status, s);
    }
}
