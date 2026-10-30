package com.ucamp.app.infra.llm.embed;

import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * EmbeddingClient 조립: LoggingEmbeddingClient → (SpringAiEmbeddingClient | Fake). LlmConfig와 같은 기준:
 * llm.provider=spring-ai(local-llm 프로필)일 때만 실제 API. 그때 EmbeddingModel은 spring.ai.model.embedding=openai로 만들어진다
 * (기본 프로필은 none → recommendation-defaults.properties)
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    /** 테스트가 벡터를 바꿔 끼울 수 있게 항상 빈으로 만든다 */
    @Bean
    public FakeEmbeddingClient fakeEmbeddingClient(@Value("${chat.embedding.model:text-embedding-3-small}") String model) {
        return new FakeEmbeddingClient(model);
    }

    @Bean
    @Primary
    public EmbeddingClient embeddingClient(LlmProperties properties, FakeEmbeddingClient fake,
                                           ObjectProvider<EmbeddingModel> embeddingModels, LlmCallLogService logService,
                                           @Value("${chat.embedding.model:text-embedding-3-small}") String model) {
        EmbeddingClient raw = fake;
        String provider = "fake";
        if (LlmProperties.PROVIDER_SPRING_AI.equalsIgnoreCase(properties.getProvider())) {
            EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();
            if (embeddingModel == null) {
                // 채팅 모델만 켠 설정(예: 추천 연동 테스트)도 서버가 뜨도록 시작 시 막지 않는다. 대화창 호출 때 실패 → 트랙 2로 간다
                log.warn("llm.provider=spring-ai인데 Spring AI EmbeddingModel이 없습니다. "
                        + "spring.ai.model.embedding=openai 를 확인하세요 (application-local-llm.yml)");
                raw = new UnavailableEmbeddingClient(model);
                provider = "unavailable";
            } else {
                raw = new SpringAiEmbeddingClient(embeddingModel, model);
                provider = "spring-ai";
            }
        }
        log.info("Embedding provider = {}, model = {}", provider, model);
        return new LoggingEmbeddingClient(raw, logService);
    }

    /** 실제 API 설정인데 EmbeddingModel이 없을 때: 가짜 벡터로 조용히 대신하지 않고 호출마다 실패시킨다 */
    record UnavailableEmbeddingClient(String model) implements EmbeddingClient {
        @Override
        public Embedding embed(String text) {
            throw new LlmCallException(LlmCallException.Kind.NETWORK, null, null, 0, "EmbeddingModel not configured", null);
        }
    }
}
