package com.ucamp.app.domain.prescription.service;

import com.ucamp.app.domain.calendar.entity.CalendarEvent;
import com.ucamp.app.domain.calendar.enums.EventStatus;
import com.ucamp.app.domain.calendar.enums.EventType;
import com.ucamp.app.domain.medicine.dto.InteractionCheckResponse;
import com.ucamp.app.domain.medicine.service.InteractionCheckService;
import com.ucamp.app.domain.prescription.dto.PrescriptionRequest;
import com.ucamp.app.domain.prescription.dto.PrescriptionResponse;
import com.ucamp.app.domain.prescription.entity.Prescription;
import com.ucamp.app.domain.calendar.repository.CalendarEventRepository;
import com.ucamp.app.domain.prescription.repository.PrescriptionRepository;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionService {

    private static final long MAX_DAYS = 62;
    private static final LocalTime FALLBACK_TIME = LocalTime.of(9, 0);

    private final PrescriptionRepository prescriptionRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final InteractionCheckService interactionCheckService;

    @Transactional
    public PrescriptionResponse create(Long userId, PrescriptionRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!userRepository.existsById(userId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        LocalDate start = request.startAt();
        LocalDate end = request.endAt();
        LocalDate scheduleEnd = end != null ? end : start.plusDays(2);
        if (scheduleEnd.isBefore(start) || ChronoUnit.DAYS.between(start, scheduleEnd) + 1 > MAX_DAYS) throw new BusinessException(ErrorCode.INVALID_INPUT);
        LocalTime eventTime = request.scheduledTime() != null ? request.scheduledTime() : FALLBACK_TIME;
        InteractionCheckResponse interactions = request.medicineId() == null ? InteractionCheckResponse.unchecked(request.name()) : interactionCheckService.check(userId, request.medicineId(), start, scheduleEnd, null);
        Prescription prescription = prescriptionRepository.save(Prescription.builder()
                .userId(userId)
                .name(request.name())
                .medicineId(request.medicineId())
                .startAt(start)
                .endAt(end)
                .build());
        String notes = (request.notes() == null || request.notes().isBlank()) ? null : request.notes().trim();
        List<CalendarEvent> events = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(scheduleEnd); d = d.plusDays(1)) {
            events.add(CalendarEvent.builder()
                    .userId(userId)
                    .prescriptionId(prescription.getPrescriptionId())
                    .eventType(EventType.MEDICATION)
                    .title(prescription.getName())
                    .scheduledAt(d.atTime(eventTime))
                    .notes(notes)
                    .status(EventStatus.PLANNED)
                    .build());
        }
        calendarEventRepository.saveAll(events);
        return PrescriptionResponse.of(prescription, events.size(), interactions);
    }

    public InteractionCheckResponse interactions(Long userId, Long prescriptionId) {
        Prescription p = prescriptionRepository.findByPrescriptionIdAndUserId(prescriptionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        if (p.getMedicineId() == null) return InteractionCheckResponse.unchecked(p.getName());
        return interactionCheckService.check(userId, p.getMedicineId(), p.getStartAt(), p.getEndAt(), p.getPrescriptionId());
    }

    @Transactional
    public PrescriptionResponse update(Long userId, Long prescriptionId, PrescriptionRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!userRepository.existsById(userId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        Prescription prescription = prescriptionRepository
                .findByPrescriptionIdAndUserId(prescriptionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        LocalDate start = request.startAt();
        LocalDate end = request.endAt();
        LocalDate scheduleEnd = end != null ? end : start.plusDays(2);
        if (scheduleEnd.isBefore(start) || ChronoUnit.DAYS.between(start, scheduleEnd) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        LocalTime eventTime = request.scheduledTime() != null ? request.scheduledTime() : FALLBACK_TIME;
        InteractionCheckResponse interactions = request.medicineId() == null ?
                InteractionCheckResponse.unchecked(request.name()) : interactionCheckService.check(userId, request.medicineId(), start, scheduleEnd, prescriptionId);
        prescription.update(request.name(), request.medicineId(), start, end);
        calendarEventRepository.deleteByPrescriptionIdAndUserId(prescriptionId, userId);
        String notes = (request.notes() == null || request.notes().isBlank()) ? null : request.notes().trim();
        List<CalendarEvent> events = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(scheduleEnd); d = d.plusDays(1)) {
            events.add(CalendarEvent.builder()
                    .userId(userId)
                    .prescriptionId(prescription.getPrescriptionId())
                    .eventType(EventType.MEDICATION)
                    .title(prescription.getName())
                    .scheduledAt(d.atTime(eventTime))
                    .notes(notes)
                    .status(EventStatus.PLANNED)
                    .build());
        }
        calendarEventRepository.saveAll(events);
        return PrescriptionResponse.of(prescription, events.size(), interactions);
    }

    @Transactional
    public void delete(Long userId, Long prescriptionId) {
        Prescription prescription = prescriptionRepository
                .findByPrescriptionIdAndUserId(prescriptionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        calendarEventRepository.deleteByPrescriptionIdAndUserId(prescriptionId, userId);
        prescriptionRepository.delete(prescription);
    }
}
