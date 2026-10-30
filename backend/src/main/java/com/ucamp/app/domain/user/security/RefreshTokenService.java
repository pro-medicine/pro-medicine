package com.ucamp.app.domain.user.security;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Opaque refresh tokens: raw values exist only in the browser cookie and request/response. */
@Service
public class RefreshTokenService {
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final Duration REMEMBER_TTL = Duration.ofDays(30);
    private static final String FAMILY_PREFIX = "auth:refresh:family:";
    private static final String ACTIVE_PREFIX = "auth:refresh:active:";
    private static final String SPENT_PREFIX = "auth:refresh:spent:";

    // Rotating and recording spent tokens must be a single Redis operation.
    private static final DefaultRedisScript<String> ROTATE = script("""
            local family = redis.call('GET', KEYS[1])
            if family then
                local familyKey = ARGV[1] .. family
                local userId = redis.call('GET', familyKey)
                local ttl = redis.call('PTTL', familyKey)
                if not userId or ttl <= 0 then
                    redis.call('DEL', KEYS[1])
                    return 'INVALID'
                end
                redis.call('SET', KEYS[2], family, 'PX', ttl)
                redis.call('SET', KEYS[3], family, 'PX', ttl)
                redis.call('DEL', KEYS[1])
                return 'OK:' .. userId .. ':' .. ttl
            end
            local spentFamily = redis.call('GET', KEYS[3])
            if spentFamily then
                redis.call('DEL', ARGV[1] .. spentFamily)
                return 'REUSED'
            end
            return 'INVALID'
            """, String.class);

    // Revoking a family prevents *all* tokens from that login session being used.
    private static final DefaultRedisScript<Long> REVOKE = script("""
            local family = redis.call('GET', KEYS[1])
            if not family then family = redis.call('GET', KEYS[2]) end
            if family then redis.call('DEL', ARGV[1] .. family) end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Issued(String value, long maxAgeSeconds) { }
    public record Rotated(Long userId, Issued token) { }

    public Issued issue(Long userId, boolean rememberMe) {
        Duration ttl = rememberMe ? REMEMBER_TTL : DEFAULT_TTL;
        String family = UUID.randomUUID().toString();
        String token = randomToken();
        // No plaintext token is persisted. Expiry is absolute from login (rotation does not extend it).
        redis.opsForValue().set(FAMILY_PREFIX + family, userId.toString(), ttl);
        redis.opsForValue().set(activeKey(token), family, ttl);
        return new Issued(token, ttl.toSeconds());
    }

    public Rotated rotate(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String next = randomToken();
        String result = redis.execute(
                ROTATE,
                List.of(activeKey(oldToken), activeKey(next), spentKey(oldToken)),
                FAMILY_PREFIX
        );
        if (result == null || !result.startsWith("OK:")) {
            // An already consumed token invalidates the entire token family in the Lua script.
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String[] parts = result.split(":", 3);
        long userId;
        long ttlMillis;
        try {
            userId = Long.parseLong(parts[1]);
            ttlMillis = Long.parseLong(parts[2]);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (userId <= 0 || ttlMillis <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new Rotated(userId, new Issued(next, (ttlMillis + 999) / 1000));
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        redis.execute(REVOKE, List.of(activeKey(token), spentKey(token)), FAMILY_PREFIX);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String activeKey(String token) { return ACTIVE_PREFIX + digest(token); }
    private String spentKey(String token) { return SPENT_PREFIX + digest(token); }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static <T> DefaultRedisScript<T> script(String body, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(body);
        script.setResultType(type);
        return script;
    }
}
