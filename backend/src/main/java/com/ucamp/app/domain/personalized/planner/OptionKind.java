package com.ucamp.app.domain.personalized.planner;

/**
 * LLM이 선택지마다 붙이는 성격 (설계 D5). 서버는 고른 선택지의 kind로 깊이·주제 전환을 정한다. 화면에는 보내지 않는다.
 * NONE: 해당 없음 / UNKNOWN: 모름 / NORMAL: 특별한 주의가 필요 없는 답 / CONCERN: 더 확인할 필요가 있는 답
 */
public enum OptionKind {
    NONE, UNKNOWN, NORMAL, CONCERN;

    /** "없어요"·"잘 모르겠어요"처럼 다른 선택지와 함께 고를 수 없는 선택지 */
    public boolean isExclusive() {
        return this == NONE || this == UNKNOWN;
    }
}
