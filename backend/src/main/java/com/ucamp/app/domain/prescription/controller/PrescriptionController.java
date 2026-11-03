package com.ucamp.app.domain.prescription.controller;

import com.ucamp.app.domain.medicine.dto.InteractionCheckResponse;
import com.ucamp.app.domain.prescription.dto.PrescriptionResponse;
import com.ucamp.app.domain.prescription.dto.PrescriptionRequest;
import com.ucamp.app.domain.prescription.service.PrescriptionService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody PrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @DeleteMapping("/{prescriptionId}")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long userId,
            @PathVariable Long prescriptionId) {
        prescriptionService.delete(userId, prescriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{prescriptionId}/interactions")
    public ResponseEntity<ApiResponse<InteractionCheckResponse>> interactions(
            @CurrentUserId Long userId,
            @PathVariable Long prescriptionId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        InteractionCheckResponse response = prescriptionService.interactions(userId, prescriptionId, startAt, endAt);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
