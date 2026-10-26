package com.ucamp.app.domain.user.service;

import com.ucamp.app.domain.user.dto.LoginRequest;
import com.ucamp.app.domain.user.dto.SignupRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.entity.AuthDemographic;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.AuthDemographicRepository;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.domain.user.security.PasswordHasher;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ucamp.app.domain.user.repository.UserRepository;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private static final Set<String> GENDERS = Set.of("여성", "남성", "선택 안 함");

    private final UserRepository users;
    private final AuthDemographicRepository demographics;
    private final PasswordHasher passwordHasher;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String name = request.name().strip();
        Integer age = request.age();

        if (name.isBlank() || age == null || age < 1 || age > 120
                || !GENDERS.contains(request.gender()) || !request.termsAgreed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (users.existsByNickname(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordHasher.hash(request.password()))
                .nickname(name)
                .build();
        try {
            users.saveAndFlush(user);
            demographics.saveAndFlush(new AuthDemographic(user, age, request.gender()));
        } catch (DataIntegrityViolationException ex) {
            // 동시에 가입 요청이 발생할 때 DB의 UNIQUE 제약을 최종적으로 보호한다.
            throw new BusinessException(ErrorCode.DUPLICATE_USER);
        }
        return UserResponse.from(user);
    }

    public UserResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return UserResponse.from(user);
    }

    public UserResponse me(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
