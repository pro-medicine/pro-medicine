package com.ucamp.app.domain.personalized.config;

import com.ucamp.app.domain.personalized.prefetch.PrefetchStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * V2 맞춤 질문 설정 (pq.*). 질문 대기 시간 개선: 미리 만들기(prefetch)와 이중 요청(hedge).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pq")
public class PersonalizedProperties {
    /** 질문 미리 만들기 방식. NONE = 질문마다 그때 1회 생성 (기존) */
    private PrefetchStrategy prefetchStrategy = PrefetchStrategy.NONE;
    /** 묶음에 넣을 프로필 주제 수 (= 새 주제 예산 5 − 불편한 점 1) */
    private int prefetchTopics = 4;
    /** 미리 만드는 중인 질문을 기다리는 최대 시간. 넘으면 그 질문은 단건 생성 */
    private long prefetchWaitMs = 6000;
    /** 단건 질문 생성이 늦으면 같은 요청을 한 번 더 보낸다 (첫 시도에만) */
    private boolean hedgeEnabled = false;
    /** 이중 요청을 보내기 전까지 기다리는 시간 */
    private long hedgeAfterMs = 8000;
}
