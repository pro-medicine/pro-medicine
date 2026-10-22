package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * V7 = V4 + 프롬프트 캐싱 (결과 캐시는 끈다). OpenAI는 앞부분이 같은 요청을 자동으로 캐싱하므로
 * cacheControl=true일 때 같은 고정 부분(시스템 프롬프트 + 스키마)끼리 묶이도록 prompt_cache_key만 보낸다 (SpringAiLlmClient).
 * 사용자마다 바뀌는 입력과 금지 성분 목록은 사용자 메시지라 고정 부분 뒤에 온다.
 * 고정 부분이 최소 캐시 길이(1024토큰)보다 짧으면 캐시가 적용되지 않는다 (프롬프트를 일부러 늘리지 않는다).
 * V4와의 차이는 prompt_cache_key뿐이라 실제 API에서 V4도 자동 캐싱이 될 수 있다 (STEP 08 리포트 참고).
 */
@Component
public class V7Pipeline extends V4Pipeline {
    public static final String VERSION = "V7";

    @Autowired
    public V7Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v4", "v4.1", false));
    }

    protected V7Pipeline(PipelineDeps deps, Options options) {
        super(deps, options);
    }

    @Override
    protected boolean promptCache() {
        return true;
    }
}
