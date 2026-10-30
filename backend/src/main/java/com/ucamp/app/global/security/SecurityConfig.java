package com.ucamp.app.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Bearer-protected APIs are stateless and not cookie-authenticated.
            // Refresh/logout use HttpOnly cookies, protected by a strict Origin check in the controller.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/login", "/api/v1/auth/signup",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/disposal/**").permitAll()
                    .requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/surveys/active"
                    ).permitAll()

                    .requestMatchers(
                            HttpMethod.POST,
                            "/api/v1/surveys/*/responses"
                    ).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${JWT_SECRET_BASE64}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT 비밀키는 최소 32바이트여야 합니다.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("yakssok"));
        return decoder;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.auth.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String csv
    ) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Credentialed cross-origin /auth/refresh and /auth/logout; origins are explicitly enumerated.
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
