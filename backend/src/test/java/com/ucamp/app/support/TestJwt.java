package com.ucamp.app.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 테스트 전용 JWT 토큰 생성기. test application.yml의 JWT_SECRET_BASE64와 같은 키를 사용한다.
 * {@code .header("Authorization", TestJwt.bearer(userId))} 형태로 MockMvc 요청에 사용.
 */
public final class TestJwt {
    private static final String SECRET_BASE64 = "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0cw==";
    private static final JwtEncoder ENCODER;

    static {
        byte[] bytes = Base64.getDecoder().decode(SECRET_BASE64);
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private TestJwt() {}

    public static String bearer(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("yakssok")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return "Bearer " + token;
    }

    public static String bearer(long userId) {
        return bearer(Long.valueOf(userId));
    }
}
