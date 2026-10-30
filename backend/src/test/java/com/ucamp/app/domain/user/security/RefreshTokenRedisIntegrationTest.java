package com.ucamp.app.domain.user.security;

import com.ucamp.app.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Requires an actual local Redis, and is opt-in to avoid depending on Redis in regular CI. */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RefreshTokenRedisIntegrationTest {
    private LettuceConnectionFactory factory;
    private RefreshTokenService service;

    @BeforeEach
    void connect() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        service = new RefreshTokenService(new StringRedisTemplate(factory));
    }

    @AfterEach
    void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void rotateAndDetectReuse() {
        var first = service.issue(12345L, false);
        var second = service.rotate(first.value());
        assertEquals(12345L, second.userId());
        assertNotEquals(first.value(), second.token().value());
        // Replaying any consumed value must revoke the whole family.
        assertThrows(BusinessException.class, () -> service.rotate(first.value()));
        assertThrows(BusinessException.class, () -> service.rotate(second.token().value()));
    }

    @Test
    void logoutRevokesOnlyItsSession() {
        var a = service.issue(12345L, true);
        var b = service.issue(12345L, true);
        service.revoke(a.value());
        assertThrows(BusinessException.class, () -> service.rotate(a.value()));
        assertEquals(12345L, service.rotate(b.value()).userId());
        service.revoke(b.value()); // previous token was rotated: also revokes its family
    }
}
