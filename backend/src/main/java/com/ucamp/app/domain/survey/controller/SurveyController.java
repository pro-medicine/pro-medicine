package com.ucamp.app.domain.survey.controller;

import com.ucamp.app.domain.survey.dto.ActiveSurveyResponse;
import com.ucamp.app.domain.survey.dto.SubmitSurveyRequest;
import com.ucamp.app.domain.survey.dto.SubmitSurveyResponse;
import com.ucamp.app.domain.survey.service.SurveyQueryService;
import com.ucamp.app.domain.survey.service.SurveySubmitService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 여부와 상관없이 설문을 할 수 있다. X-User-Id 헤더가 있으면 회원(임시)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/surveys")
public class SurveyController {
    private final SurveyQueryService surveyQueryService;
    private final SurveySubmitService surveySubmitService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<ActiveSurveyResponse>> getActive(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(surveyQueryService.getActiveSurvey(userId)));
    }

    @PostMapping("/{surveyId}/responses")
    public ResponseEntity<ApiResponse<SubmitSurveyResponse>> submit(@PathVariable Long surveyId,
                                                                   @CurrentUserId Long userId,
                                                                   @RequestBody SubmitSurveyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(surveySubmitService.submit(surveyId, userId, request)));
    }
}
