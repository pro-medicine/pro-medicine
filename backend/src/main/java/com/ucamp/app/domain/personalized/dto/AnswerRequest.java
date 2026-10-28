package com.ucamp.app.domain.personalized.dto;

import java.util.List;

/** 질문 하나에 대한 답. selected = 고른 선택지 index (고른 순서) */
public record AnswerRequest(Long turnId, List<Integer> selected) {
}
