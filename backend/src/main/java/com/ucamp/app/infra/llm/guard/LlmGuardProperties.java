package com.ucamp.app.infra.llm.guard;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * app.llm-guard.* 설정 (V5 보안).
 * 기본값은 recommendation-defaults.properties에 두고, application.yml이나 프로필로 재정의한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm-guard")
public class LlmGuardProperties {

    /** 기능별 가드 ON/OFF. 키: recommendation, personalized, chat 등. Redis 키 llm:kill:{feature}가 우선 */
    private Map<String, Boolean> enabled = new HashMap<>();

    /** 사용자별 분/일 제한 — 기본값 (기능별 설정이 없으면 이 값 사용) */
    private Limits limits = new Limits();

    /** 기능별 분/일 제한. 키: recommendation, personalized, chat 등 */
    private Map<String, Limits> featureLimits = new HashMap<>();

    /** inflight 잠금 TTL (초). 같은 사용자의 동시 LLM 요청을 하나로 제한 */
    private int inflightTtlSeconds = 120;

    /** 경고 누적 → 정지 → 차단 설정 */
    private Strikes strikes = new Strikes();

    /** 기능별 일일 예산 (실제 LLM 호출 단위). 키: recommendation, personalized, chat 등 */
    private Map<String, Integer> dailyBudget = new HashMap<>();

    /** 기능별 max_tokens 설정 */
    private Map<String, Integer> maxTokens = new HashMap<>();

    public boolean isFeatureEnabled(String feature) {
        return enabled.getOrDefault(feature, true);
    }

    public int getDailyBudgetFor(String feature) {
        return dailyBudget.getOrDefault(feature, 5000);
    }

    public int getMaxTokensFor(String feature) {
        return maxTokens.getOrDefault(feature, 2000);
    }

    /** 기능별 분/일 제한 조회. 기능별 설정이 없으면 기본 limits 사용 */
    public Limits getLimitsFor(String feature) {
        return featureLimits.getOrDefault(feature, limits);
    }

    @Getter
    @Setter
    public static class Limits {
        private int perMinute = 10;
        private int perDay = 100;
    }

    @Getter
    @Setter
    public static class Strikes {
        /** 24시간 내 경고 N회 → 30분 정지 */
        private int suspendThreshold = 3;
        /** 24시간 내 경고 N회 → 24시간 정지 */
        private int longSuspendThreshold = 6;
        /** 30분 정지 시간 (초) */
        private int suspendSeconds = 1800;
        /** 24시간 정지 시간 (초) */
        private int longSuspendSeconds = 86400;
    }
}
