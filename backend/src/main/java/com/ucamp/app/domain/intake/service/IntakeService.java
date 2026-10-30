package com.ucamp.app.domain.intake.service;

import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.dto.TakenIntake;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.intake.repository.IntakeRecordRepository;
import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.notification.event.IntakeCheckedEvent;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import java.time.temporal.ChronoUnit; // 달력 범위


import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 연속 복용일은 최근 1년까지만 계산
    private static final int MAX_STREAK_DAYS = 365;
    private static final long MAX_CALENDAR_DAYS = 62;


    private final IntakeScheduleRepository intakeScheduleRepository;
    private final IntakeRecordRepository intakeRecordRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;



    public IntakeStatsResponse getStats(Long userId) {
        return getStats(userId, LocalDate.now(KST));
    }

    // 기준일을 받아서 계산 (테스트에서 날짜를 고정할 수 있게 분리)
    public IntakeStatsResponse getStats(Long userId, LocalDate today) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        List<IntakeSchedule> schedules = intakeScheduleRepository.findActiveSchedules(userId);
        LocalDate from = today.minusDays(MAX_STREAK_DAYS);
        Map<LocalDate, Set<Long>> takenByDate = new HashMap<>();
        for (TakenIntake taken : intakeRecordRepository.findTakenIntakes(userId, from, today)) { //intakeRecordRepository로 수정
            takenByDate.computeIfAbsent(taken.intakeDate(), date -> new HashSet<>()).add(taken.scheduleId());
        }

        return new IntakeStatsResponse(
                userId,
                streakDays(schedules, takenByDate, today),
                intakeRecordRepository.countActiveSupplements(userId), //수정
                monthlyRate(schedules, takenByDate, today)
        );
    }

    // 그날 먹어야 하는 일정을 모두 먹은 날이 오늘(또는 어제)부터 며칠 연속인지
    // 오늘은 아직 끝나지 않았으므로, 오늘을 다 먹지 않았으면 어제부터 센다
    private int streakDays(List<IntakeSchedule> schedules, Map<LocalDate, Set<Long>> takenByDate, LocalDate today) {
        int streak = 0;
        LocalDate day = today;
        if (!isCompleted(schedules, takenByDate, today)) {
            day = today.minusDays(1);
        }
        while (streak < MAX_STREAK_DAYS && isCompleted(schedules, takenByDate, day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    // 이번 달 1일부터 오늘까지 (먹은 횟수 / 먹어야 했던 횟수) * 100, 먹어야 할 일정이 없으면 0
    private int monthlyRate(List<IntakeSchedule> schedules, Map<LocalDate, Set<Long>> takenByDate, LocalDate today) {
        long expected = 0;
        long taken = 0;
        for (LocalDate day = today.withDayOfMonth(1); !day.isAfter(today); day = day.plusDays(1)) {
            Set<Long> takenIds = takenByDate.getOrDefault(day, Set.of());
            for (IntakeSchedule schedule : schedules) {
                if (isScheduledOn(schedule, day)) {
                    expected++;
                    if (takenIds.contains(schedule.getScheduleId())) {
                        taken++;
                    }
                }
            }
        }
        return expected == 0 ? 0 : (int) Math.round(taken * 100.0 / expected);
    }

    // 하루 동안 영양제를 다 섭취 했는지 여부 확인
    private boolean isCompleted(List<IntakeSchedule> schedules, Map<LocalDate, Set<Long>> takenByDate, LocalDate day) {
        Set<Long> takenIds = takenByDate.getOrDefault(day, Set.of());
        boolean hasSchedule = false;
        for (IntakeSchedule schedule : schedules) {
            if (isScheduledOn(schedule, day)) {
                hasSchedule = true;
                if (!takenIds.contains(schedule.getScheduleId())) {
                    return false;
                }
            }
        }
        return hasSchedule;
    }

    // 일정이 만들어진 날과 복용 시작일 중 늦은 날부터 매일 먹어야 하는 것으로 본다
    private boolean isScheduledOn(IntakeSchedule schedule, LocalDate day) {
        LocalDate begin = schedule.getCreatedAt().toLocalDate();
        LocalDate startDate = schedule.getUserProduct().getStartDate();
        if (startDate != null && startDate.isAfter(begin)) {
            begin = startDate;
        }
        return !day.isBefore(begin);
    }

    @Transactional
    public void updateIntake(Long userId, Long scheduleId, LocalDate date, boolean taken) { //수정
        // TODO: 만약 복용체크를 복용 스케줄 기준으로 할 거라면
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
            // TODO: 커스텀 비즈니스 에러로 수정
        }
        //남을 일정 체크하지 못하도록
        if (intakeScheduleRepository.countMySchedule(userId, scheduleId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (taken) {
            if (intakeRecordRepository.updateTaken(scheduleId, date) == 0) {
                intakeRecordRepository.insertTaken(scheduleId, date);
            }
            eventPublisher.publishEvent(new IntakeCheckedEvent(userId, date));
        } else {
            intakeRecordRepository.deleteRecord(scheduleId, date);
        }
    }

    // 홈 화면 "오늘 먹을 영양제" 목록
    public List<TodayIntakeResponse> getTodayIntakes(Long userId, LocalDate date) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return intakeScheduleRepository.findTodaySupplements(userId, date)
                .stream()
                .map(row -> {
                    TodayIntakeResponse dto = new TodayIntakeResponse();
                    dto.setId(row.getId());
                    dto.setTiming(row.getTiming());
                    dto.setName(row.getName());
                    dto.setDesc(row.getDesc());
                    dto.setTaken(row.getTaken() != null && row.getTaken().intValue() == 1); // 먹었으면 true
                    dto.setMealSlot(row.getMealSlot());
                    dto.setRemindAt(toHourMinute(row.getRemindAt()));
                    dto.setCompletedAt(toHourMinute(row.getCompletedAt()));
                    return dto;
                })
                .toList();
    }

    // native query의 시간 값 → "HH:mm" (DB 드라이버에 따라 Time / LocalTime / Timestamp / LocalDateTime 으로 올 수 있음)
    static String toHourMinute(Object value) {
        if (value == null) return null;
        java.time.LocalTime time;
        if (value instanceof java.time.LocalTime t) {
            time = t;
        } else if (value instanceof java.sql.Time t) {
            time = t.toLocalTime();
        } else if (value instanceof java.time.LocalDateTime t) {
            time = t.toLocalTime();
        } else if (value instanceof java.sql.Timestamp t) {
            time = t.toLocalDateTime().toLocalTime();
        } else {
            String text = value.toString();               // "08:00:00" 또는 "2026-10-29 08:10:00"
            int split = Math.max(text.indexOf(' '), text.indexOf('T'));
            if (split >= 0) text = text.substring(split + 1);
            return text.length() >= 5 ? text.substring(0, 5) : text;
        }
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    // 달력 만들기 부분
    public List<LocalDate> getCompletedDates(Long userId, LocalDate start, LocalDate end) {
        // 0. 기간 검사: start가 end보다 늦거나, 62일(2달 최대 일수 기준)을 넘으면 400 에러
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_CALENDAR_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 1. 없는 사용자면 에러
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 먹어야 할 일정 가져오기
        List<IntakeSchedule> schedules = intakeScheduleRepository.findActiveSchedules(userId);

        // 3. 기간 안의 먹은 기록(TAKEN)을 날짜별로 묶기: { 날짜 → 그날 먹은 scheduleId 모음 }
        Map<LocalDate, Set<Long>> takenByDate = new HashMap<>();
        for (TakenIntake taken : intakeRecordRepository.findTakenIntakes(userId, start, end)) {
            takenByDate.computeIfAbsent(taken.intakeDate(), d -> new HashSet<>()).add(taken.scheduleId());
        }

        // 4. 하루씩 보면서 다 먹은 날(isCompleted == true)만 담기 (미래 날짜는 제외)
        LocalDate today = LocalDate.now(KST);
        List<LocalDate> completedDates = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end) && !day.isAfter(today); day = day.plusDays(1)) {
            if (isCompleted(schedules, takenByDate, day)) {
                completedDates.add(day);
            }
        }
        return completedDates;
    }

    public Map<Long, Integer> getStreakDays(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(MAX_STREAK_DAYS);
        // 1. 사용자별 일정, 일정 id → 사용자 id
        Map<Long, List<IntakeSchedule>> schedulesByUser = new HashMap<>();
        Map<Long, Long> ownerBySchedule = new HashMap<>();
        for (IntakeSchedule schedule : intakeScheduleRepository.findActiveSchedulesByUserIds(userIds)) {
            Long ownerId = schedule.getUserProduct().getUser().getId();
            schedulesByUser.computeIfAbsent(ownerId, id -> new ArrayList<>()).add(schedule);
            ownerBySchedule.put(schedule.getScheduleId(), ownerId);
        }

        // 2. 사용자별 { 날짜 → 먹은 scheduleId }
        Map<Long, Map<LocalDate, Set<Long>>> takenByUser = new HashMap<>();
        for (TakenIntake taken : intakeRecordRepository.findTakenIntakesByUserIds(userIds, from, today)) {
            Long ownerId = ownerBySchedule.get(taken.scheduleId());
            if (ownerId == null) continue;   // 꺼진 일정의 기록은 계산에서 제외 (단일 조회와 동일)
            takenByUser
                    .computeIfAbsent(ownerId, id -> new HashMap<>())
                    .computeIfAbsent(taken.intakeDate(), d -> new HashSet<>())
                    .add(taken.scheduleId());
        }

        // 3. 사용자별 스트릭 (일정이 없으면 0)
        Map<Long, Integer> result = new HashMap<>();
        for (Long userId : userIds) {
            List<IntakeSchedule> schedules = schedulesByUser.getOrDefault(userId, List.of());
            Map<LocalDate, Set<Long>> takenByDate = takenByUser.getOrDefault(userId, Map.of());
            result.put(userId, streakDays(schedules, takenByDate, today));
        }
        return result;
    }
}
