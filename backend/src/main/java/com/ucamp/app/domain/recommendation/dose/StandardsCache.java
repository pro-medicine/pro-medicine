package com.ucamp.app.domain.recommendation.dose;

import com.ucamp.app.domain.recommendation.repository.IngredientStandardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * ingredient_standards 메모리 캐시. 데이터가 작고(수십 행) 거의 바뀌지 않아 앱 시작 시 한 번 읽는다.
 * 변경 반영: 기준표 SQL을 바꾼 뒤 서버를 재시작하거나 reload()를 호출한다 (관리 API는 아직 없음).
 * dataVersion()은 행 내용의 해시라 결과 캐시 키(STEP 08)에 넣으면 기준이 바뀔 때 캐시가 자동 무효화된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandardsCache {
    private final IngredientStandardRepository repository;
    private volatile Snapshot snapshot;

    private record Snapshot(List<Standard> standards, String version) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        List<Standard> standards = repository.findAll().stream().map(Standard::from).toList();
        snapshot = new Snapshot(standards, hash(standards.toString()));
        log.info("ingredient_standards loaded: {} rows, version {}", standards.size(), snapshot.version());
    }

    public List<Standard> standards() {
        return current().standards();
    }

    public String dataVersion() {
        return current().version();
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null) {
            reload();
            s = snapshot;
        }
        return s;
    }

    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
