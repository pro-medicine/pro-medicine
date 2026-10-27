package com.ucamp.app.support;

import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;

import java.util.concurrent.atomic.AtomicInteger;

// 테스트용 회원 생성 (예시 데이터)
public final class TestUsers {
    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestUsers() {
    }

    public static User create(UserRepository repository, boolean consent) {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .email("test" + n + "@example.com")
                .passwordHash("{noop}test")
                .nickname("tester" + n)
                .build();
        if (consent) {
            user.agreeSensitiveConsent();
        }
        return repository.saveAndFlush(user);
    }
}
