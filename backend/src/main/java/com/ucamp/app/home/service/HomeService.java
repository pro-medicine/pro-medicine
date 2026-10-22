package com.ucamp.app.home.service;

import com.ucamp.app.home.dto.HomeResponse;
import com.ucamp.app.home.dto.HomeSupplementResponse;
import com.ucamp.app.home.repository.HomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeRepository homeRepository;

    @Transactional(readOnly = true)
    public HomeResponse getHome(Long userId, LocalDate date) {
        String nickname = homeRepository.findNickname(userId);
        if (nickname == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음: " + userId);
        }

        // DB 결과(1/0) → 화면용 DTO(true/false)
        List<HomeSupplementResponse> supplements = homeRepository.findTodaySupplements(userId, date)
                .stream()
                .map(row -> {
                    HomeSupplementResponse dto = new HomeSupplementResponse();
                    dto.setId(row.getId());
                    dto.setTiming(row.getTiming());
                    dto.setName(row.getName());
                    dto.setDesc(row.getDesc());
                    dto.setTaken(row.getTaken() != null && row.getTaken().intValue() == 1);
                    return dto;
                })
                .toList();

        HomeResponse res = new HomeResponse();
        res.setUserName(nickname);
        res.setToday(date.toString());
        res.setHasUnreadNotifications(homeRepository.countUnread(userId) > 0);
        res.setSupplements(supplements);
        return res;
    }

    @Transactional
    public void updateIntake(Long userId, Long scheduleId, LocalDate date, boolean taken) {
        if (homeRepository.countMySchedule(userId, scheduleId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 복용 일정이 아님: " + scheduleId);
        }
        if (taken) {
            if (homeRepository.updateTaken(scheduleId, date) == 0) {
                homeRepository.insertTaken(scheduleId, date);
            }
        } else {
            homeRepository.deleteRecord(scheduleId, date);   // 체크 해제 = 기록 삭제
        }
    }
}