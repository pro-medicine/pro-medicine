package com.ucamp.app.domain.disposal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ucamp.app.domain.disposal.service.DisposalService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/disposal")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class DisposalController {

    private final DisposalService disposalService;

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

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<JsonNode>> searchNearby(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "1000") Integer radius) {

        if (latitude == null || longitude == null ||
                !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
                latitude < -90 || latitude > 90 ||
                longitude < -180 || longitude > 180 ||
                radius == null ||
                (radius != 500 && radius != 1000 && radius != 2000 && radius != 3000)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("D003", "올바른 위치 좌표와 검색 반경(500/1000/2000/3000m)을 입력해주세요."));
        }

        return ResponseEntity.ok(ApiResponse.success(
                disposalService.searchNearby(latitude, longitude, radius)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("D003", "위도, 경도 및 검색 반경은 숫자로 입력해주세요."));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleKakaoApiError(RestClientException ex) {
        log.error("카카오 장소 검색 API 호출 실패", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("D002", "카카오 장소 검색 API 호출에 실패했습니다."));
    }
}
