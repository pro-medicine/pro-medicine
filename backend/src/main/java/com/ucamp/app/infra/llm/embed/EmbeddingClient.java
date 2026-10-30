package com.ucamp.app.infra.llm.embed;

/**
 * 문장 1개 → 임베딩 벡터 (V4 대화창 검색 문장용). 리뷰 언급 벡터와 같은 모델이어야 코사인 비교가 의미가 있다.
 * 실패하면 LlmCallException (채팅 호출과 같은 예외)
 */
public interface EmbeddingClient {
    String TAG_CHAT_EMBED = "CHAT_EMBED";

    Embedding embed(String text);

    /** 이 클라이언트가 쓰는 모델 이름 (리뷰 언급의 embedding_model과 비교) */
    String model();

    /**
     * @param vector 모델이 준 벡터 그대로 (정규화는 사용하는 쪽에서)
     * @param inputTokens 사용 토큰 (모르면 null)
     * @param callLogId   llm_call_logs 행 id (기록하지 않았으면 null)
     */
    record Embedding(float[] vector, String model, Integer inputTokens, long latencyMs, Long callLogId) {
        public Embedding withCallLogId(Long id) {
            return new Embedding(vector, model, inputTokens, latencyMs, id);
        }
    }
}
