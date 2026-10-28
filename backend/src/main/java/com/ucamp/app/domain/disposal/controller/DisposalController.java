package com.ucamp.app.domain.disposal.controller;

import tools.jackson.databind.JsonNode;
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
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;

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
            throw new BusinessException(ErrorCode.DISPOSAL_INVALID_QUERY);
        }

        try {
            JsonNode result = disposalService.searchPlaces(query.trim());

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (RuntimeException e) {
            log.error("[DISPOSAL] 약국 검색 실패. query={}", query, e);
            throw e;
        }
    }


    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<JsonNode>> searchNearby(
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(defaultValue = "1000") String radius) {

        if (latitude == null || longitude == null || radius == null) {
            throw new BusinessException(ErrorCode.DISPOSAL_INVALID_LOCATION);
        }

        double parsedLatitude;
        double parsedLongitude;
        int parsedRadius;

        try {
            parsedLatitude = Double.parseDouble(latitude);
            parsedLongitude = Double.parseDouble(longitude);
            parsedRadius = Integer.parseInt(radius);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.DISPOSAL_INVALID_LOCATION);
        }

        if (!Double.isFinite(parsedLatitude) || !Double.isFinite(parsedLongitude) ||
                parsedLatitude < -90 || parsedLatitude > 90 ||
                parsedLongitude < -180 || parsedLongitude > 180 ||
                (parsedRadius != 500 && parsedRadius != 1000 &&
                        parsedRadius != 2000 && parsedRadius != 3000)) {
            throw new BusinessException(ErrorCode.DISPOSAL_INVALID_LOCATION);
        }

        return ResponseEntity.ok(ApiResponse.success(
                disposalService.searchNearby(parsedLatitude, parsedLongitude, parsedRadius)));
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
