
package com.ucamp.app.domain.disposal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ucamp.app.domain.disposal.service.DisposalService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/disposal")
public class DisposalController {

    private final DisposalService disposalService;
    @CrossOrigin(origins = {
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    })
    @GetMapping("/places")
    public ResponseEntity<ApiResponse<JsonNode>> searchPlaces(
            @RequestParam(required = false) String query) {

        if (query == null || query.isBlank() || query.trim().length() > 100) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("D001", "검색어는 1~100자로 입력해주세요."));
        }

        return ResponseEntity.ok(
                ApiResponse.success(disposalService.searchPlaces(query.trim())));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleKakaoApiError(RestClientException ex) {
        log.error("카카오 장소 검색 API 호출 실패", ex);

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("D002", "카카오 장소 검색 API 호출에 실패했습니다."));
    }
}
