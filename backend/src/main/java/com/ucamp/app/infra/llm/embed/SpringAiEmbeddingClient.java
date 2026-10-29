package com.ucamp.app.infra.llm.embed;

import com.ucamp.app.infra.llm.LlmCallException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Spring AI EmbeddingModel(OpenAI)로 임베딩. 모델은 spring.ai.openai.embedding.options.model (= chat.embedding.model)
 * local-llm 프로필에서만 쓴다 (spring.ai.model.embedding=openai). 기본 프로필은 FakeEmbeddingClient
 */
@RequiredArgsConstructor
public class SpringAiEmbeddingClient implements EmbeddingClient {
    private final EmbeddingModel embeddingModel;
    private final String model;

    @Override
    public Embedding embed(String text) {
        long start = System.nanoTime();
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            long latency = (System.nanoTime() - start) / 1_000_000;
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            String responseModel = response.getMetadata() == null ? null : response.getMetadata().getModel();
            return new Embedding(response.getResult().getOutput(),
                    responseModel == null || responseModel.isBlank() ? model : responseModel,
                    usage == null ? null : usage.getPromptTokens(), latency, null);
        } catch (LlmCallException e) {
            throw e;
        } catch (RuntimeException e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            throw new LlmCallException(LlmCallException.Kind.NETWORK, null, null, latency,
                    "embedding failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public String model() {
        return model;
    }
}
