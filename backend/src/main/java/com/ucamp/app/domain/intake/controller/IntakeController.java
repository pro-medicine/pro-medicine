package com.ucamp.app.domain.intake.controller;

import com.ucamp.app.domain.intake.dto.IntakeRequest;
import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl; //달력 cache

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/intakes")
@CrossOrigin(
        origins = "${app.cors.allowed-origins:http://localhost:5173}",
        methods = {RequestMethod.GET, RequestMethod.POST},
        allowedHeaders = {"Content-Type", "X-User-Id"}
)
public class IntakeController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IntakeService intakeService;
    private final UserService userService;

    // 복용 체크 저장: POST /api/v1/intakes  (헤더 X-User-Id, body { scheduleId, taken, date })
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> updateIntake(
            @CurrentUserId Long userId,
            @RequestBody IntakeRequest req
    ) {
        requireLogin(userId);
        LocalDate date = req.getDate() != null ? req.getDate() : LocalDate.now(KST);
        intakeService.updateIntake(userId, req.getScheduleId(), date, req.isTaken());
        return ResponseEntity.noContent().build();   // 204
    }

    // 마이페이지 통계 (마이페이지 프론트가 ?userId=로 호출 중이라 그대로 둠, 담당자 협의 후 변경)
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<IntakeStatsResponse>> getStats(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(intakeService.getStats(userId)));
    }

    // 홈 화면 오늘 먹을 영양제: GET /api/v1/intakes/today?date=2026-10-21  (헤더 X-User-Id, date 없으면 오늘)
    @GetMapping("/today")
    public ResponseEntity<List<TodayIntakeResponse>> getTodayIntakes(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        requireLogin(userId);
        LocalDate target = date != null ? date : LocalDate.now(KST);
        userService.markActive(userId);
        return ResponseEntity.ok(intakeService.getTodayIntakes(userId, target));
    }

    // TEMP: 로그인 연동 전까지 X-User-Id 헤더가 없으면(비회원) null로 들어오므로 401로 막는다
    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    //달력
    // 응답: 그날 먹어야 할 일정을 전부 먹은 날짜 목록
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<LocalDate>>> getCalendar(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, //이 start 기간부터
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end //end 기간까지만 isCompleted 조회 (API 하나로 주간, 월간 처리)
    ) {
        requireLogin(userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 건강 정보라 브라우저·중간 서버에 응답을 저장하지 않음
                .body(ApiResponse.success(intakeService.getCompletedDates(userId, start, end)));
    }

}