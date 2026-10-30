package com.ucamp.app.domain.chat.controller;

import com.ucamp.app.domain.chat.dto.ChatMessageRequest;
import com.ucamp.app.domain.chat.dto.ChatMessageResponse;
import com.ucamp.app.domain.chat.dto.ChatSessionResponse;
import com.ucamp.app.domain.chat.dto.ChatStartResponse;
import com.ucamp.app.domain.chat.service.ChatRecommendationService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// V4 대화창 (증상 문장 → 성분 + 제품). 회원 전용 (X-User-Id 없으면 401). TODO: 로그인 연동 시 @CurrentUserId만 교체
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {
    private final ChatRecommendationService chatService;

    // 대화 시작 (19~50세 여성은 임신·수유 고정 질문 포함)
    @PostMapping
    public ResponseEntity<ApiResponse<ChatStartResponse>> start(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.start(member(userId))));
    }

    // 입력 → CLARIFY | RED_FLAG | OFF_TOPIC | RESULT | PENDING
    @PostMapping("/{chatSessionId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> message(@CurrentUserId Long userId,
                                                                    @PathVariable Long chatSessionId,
                                                                    @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatService.message(member(userId), chatSessionId, request)));
    }

    // 결과 다시 보기
    @GetMapping("/{chatSessionId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> get(@CurrentUserId Long userId,
                                                                @PathVariable Long chatSessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.get(member(userId), chatSessionId)));
    }

    static Long member(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
