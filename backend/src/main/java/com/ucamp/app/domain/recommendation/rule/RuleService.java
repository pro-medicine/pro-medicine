package com.ucamp.app.domain.recommendation.rule;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.repository.RecommendationRuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
 * 규칙 테이블 메모리 캐시 + RuleEngine 호출 + 규칙 적중 Counter(recommendation.rule.hit {rule_id, phase}).
 * 로그에는 질환·약 원문 대신 규칙 ID만 남긴다. 규칙 변경은 재시작 또는 reload()로 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleService {
    public static final String HIT_METRIC = "recommendation.rule.hit";

    private final RecommendationRuleRepository repository;
    private final ConditionCodeResolver conditionCodeResolver;
    private final MeterRegistry meterRegistry;
    private volatile Snapshot snapshot;

    private record Snapshot(RuleEngine engine, String version) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        List<Rule> rules = repository.findByActiveTrueOrderByRuleIdAsc().stream().map(Rule::from).toList();
        snapshot = new Snapshot(new RuleEngine(rules), hash(rules.toString()));
        log.info("recommendation_rules loaded: {} rules, version {}", rules.size(), snapshot.version());
    }

    public List<RuleEngine.Banned> preFilter(RecommendationInput input) {
        List<RuleEngine.Banned> banned = engine().preFilter(conditionCodeResolver.resolve(input), input.isGuest());
        banned.forEach(b -> count(b.rule().ruleId(), "pre"));
        return banned;
    }

    public List<RecommendationItem> applyPost(RecommendationInput input, List<RecommendationItem> items) {
        RuleEngine.PostResult result = engine().applyPost(items, conditionCodeResolver.resolve(input), input.isGuest());
        result.hitRuleIds().forEach(id -> count(id, "post"));
        return result.items();
    }

    public RuleEngine engine() {
        return current().engine();
    }

    public String dataVersion() {
        return current().version();
    }

    private void count(String ruleId, String phase) {
        meterRegistry.counter(HIT_METRIC, "rule_id", ruleId, "phase", phase).increment();
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null) {
            reload();
            s = snapshot;
        }
        return s;
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
