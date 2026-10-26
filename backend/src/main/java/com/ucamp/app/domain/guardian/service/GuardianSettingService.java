package com.ucamp.app.domain.guardian.service;

import com.ucamp.app.domain.guardian.repository.GuardianSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianSettingService {

    private final GuardianSettingRepository guardianSettingRepository;

}
