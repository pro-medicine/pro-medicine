package com.ucamp.app.domain.calendar.service;

import com.ucamp.app.domain.calendar.dto.PrescriptionResponse;
import com.ucamp.app.domain.calendar.entity.UserPrescription;
import com.ucamp.app.domain.calendar.repository.UserPrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPrescriptionService {

    private final UserPrescriptionRepository userPrescriptionRepository;

    public List<PrescriptionResponse> getPrescriptions(Long userId) {
        List<UserPrescription> list = userPrescriptionRepository.findAllByUserId(userId);
        return list.stream().map(PrescriptionResponse::from).toList();
    }
}
