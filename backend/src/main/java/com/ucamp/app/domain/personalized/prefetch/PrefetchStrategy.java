package com.ucamp.app.domain.personalized.prefetch;

/** 질문 미리 만들기 방식 (pq.prefetch-strategy) */
public enum PrefetchStrategy {
    /** 질문마다 그때 1회 생성 (기존, 기준선) */
    NONE,
    /** ① 첫 질문은 바로 만들고, 동시에 백그라운드에서 프로필 주제 1단계를 묶음 1회로 생성 */
    BACKGROUND,
    /** ② 회차 시작 시 불편한 점 + 프로필 주제 1단계를 한 번에 생성 */
    UPFRONT,
    /** ③ ②에 더해 파생 주제 1단계 묶음, 각 1단계의 "CONCERN이면 이어질 2단계"까지 미리 생성 */
    UPFRONT_FOLLOWUP;

    public boolean upfront() {
        return this == UPFRONT || this == UPFRONT_FOLLOWUP;
    }

    public boolean followup() {
        return this == UPFRONT_FOLLOWUP;
    }
}
