package com.ucamp.app.domain.medicine.controller;

import com.ucamp.app.domain.medicine.dto.InteractionCheckResponse;
import com.ucamp.app.domain.medicine.dto.MedicineSummary;
import com.ucamp.app.domain.medicine.service.InteractionCheckService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/medicines")
public class MedicineController {

    private final InteractionCheckService interactionCheckService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MedicineSummary>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(interactionCheckService.search(q)));
    }

    @GetMapping("/{medicineId}/interactions")
    public ResponseEntity<ApiResponse<InteractionCheckResponse>> previewInteractions(
            @CurrentUserId Long userId,
            @PathVariable Long medicineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
            @RequestParam(required = false) Long excludePrescriptionId) {   // 추가
        LocalDate start = startAt != null ? startAt : LocalDate.now();
        LocalDate end = endAt != null ? endAt : start.plusDays(2);
        return ResponseEntity.ok(ApiResponse.success(
                interactionCheckService.check(userId, medicineId, start, end, excludePrescriptionId)));  // null -> excludePrescriptionId
    }
}