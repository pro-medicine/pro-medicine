package com.ucamp.app.domain.chat.vector;

import com.ucamp.app.domain.chat.config.ChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 리뷰 언급 벡터를 서버 메모리에 올려 Java로 코사인 계산 (설계 5-1, D8). 커뮤니티판 MySQL은 벡터 거리 함수가 없다.
 * 대상: RESOLVED + (RECOMMEND | NOT_RECOMMEND) + embedding 있음. 벡터는 L2 정규화해 두므로 코사인 = 내적.
 * import는 SQL 수동 실행이라 서버가 모른다 → refresh-interval마다 MAX(review_product_scores.computed_at)을 보고
 * 바뀌었으면 통째로 다시 읽어 교체 (교체 중 요청은 이전 인덱스를 쓴다)
 */
@Slf4j
@Component
public class InMemoryReviewVectorIndex implements ReviewVectorIndex {
    public static final int DIMENSIONS = 1536;
    static final int EMBEDDING_BYTES = DIMENSIONS * Float.BYTES;

    private static final String MENTIONS_SQL = """
            SELECT mention_id, channel_id, video_id, catalog_product_id, ingredients, stance, is_sponsored,
                   published_at, embedding, embedding_model
            FROM review_mentions
            WHERE resolve_status = 'RESOLVED' AND stance IN ('RECOMMEND', 'NOT_RECOMMEND') AND embedding IS NOT NULL
            ORDER BY mention_id
            """;
    private static final String VERSION_SQL = "SELECT MAX(computed_at) FROM review_product_scores";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;
    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.EMPTY);

    record Entry(Mention mention, float[] vector) {
    }

    record Snapshot(List<Entry> entries, LocalDateTime dataVersion, boolean loaded) {
        static final Snapshot EMPTY = new Snapshot(List.of(), null, false);
    }

    public InMemoryReviewVectorIndex(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, ChatProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        reload();
    }

    @Scheduled(fixedDelayString = "${chat.index.refresh-interval:10m}",
            initialDelayString = "${chat.index.refresh-interval:10m}")
    public void refreshIfChanged() {
        LocalDateTime version = readVersion();
        Snapshot snap = current.get();
        if (!snap.loaded() || !Objects.equals(version, snap.dataVersion())) {
            reload();
        }
    }

    /** DB에서 다시 읽어 교체. 실패하면 이전 인덱스를 그대로 둔다 */
    public synchronized void reload() {
        try {
            LocalDateTime version = readVersion();
            List<Entry> entries = readEntries();
            current.set(new Snapshot(List.copyOf(entries), version, true));
            log.info("Review vector index loaded: {} mentions, dataVersion = {}", entries.size(), version);
        } catch (RuntimeException e) {
            // review_mentions가 아직 없는 DB(AI_SETUP 실행 전) 등. 인덱스는 비어 있고 대화창은 트랙 2로 간다
            log.warn("Review vector index load failed, keep previous ({} mentions): {}", current.get().entries().size(),
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public List<Hit> search(float[] query, double tau, int k) {
        float[] q = normalize(query);
        if (q == null) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Entry e : current.get().entries()) {
            if (e.vector().length != q.length) {
                continue;
            }
            double sim = dot(q, e.vector());
            if (sim >= tau) {
                hits.add(new Hit(e.mention(), sim));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::similarity).reversed()
                .thenComparingLong(h -> h.mention().mentionId()));
        return hits.size() <= k ? List.copyOf(hits) : List.copyOf(hits.subList(0, k));
    }

    @Override
    public int size() {
        return current.get().entries().size();
    }

    @Override
    public LocalDateTime dataVersion() {
        return current.get().dataVersion();
    }

    private LocalDateTime readVersion() {
        Timestamp ts = jdbcTemplate.queryForObject(VERSION_SQL, Timestamp.class);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private List<Entry> readEntries() {
        String model = properties.getEmbedding().getModel();
        List<Entry> out = new ArrayList<>();
        int[] skipped = new int[2];
        jdbcTemplate.query(MENTIONS_SQL, rs -> {
            long id = rs.getLong("mention_id");
            String rowModel = rs.getString("embedding_model");
            if (!model.equals(rowModel)) {
                skipped[0]++;
                log.warn("review_mentions {} skipped: embedding_model {} != {}", id, rowModel, model);
                return;
            }
            float[] vector = normalize(decode(rs.getBytes("embedding")));
            if (vector == null) {
                skipped[1]++;
                log.warn("review_mentions {} skipped: embedding is not {} bytes", id, EMBEDDING_BYTES);
                return;
            }
            Date published = rs.getDate("published_at");
            long productId = rs.getLong("catalog_product_id");
            Long product = rs.wasNull() ? null : productId;
            out.add(new Entry(new Mention(id, rs.getString("channel_id"), rs.getString("video_id"), product,
                    ingredients(rs.getString("ingredients")), rs.getString("stance"), rs.getBoolean("is_sponsored"),
                    published == null ? null : published.toLocalDate()), vector));
        });
        if (skipped[0] + skipped[1] > 0) {
            log.warn("Review vector index skipped {} rows (model mismatch {}, bad length {})",
                    skipped[0] + skipped[1], skipped[0], skipped[1]);
        }
        return out;
    }

    private List<String> ingredients(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(json)) {
                if (node.isString() && !node.asString().isBlank()) {
                    out.add(node.asString());
                }
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** MEDIUMBLOB(float32 little-endian x 1536, numpy tobytes) → float[]. 길이가 다르면 null */
    static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length != EMBEDDING_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[DIMENSIONS];
        buffer.asFloatBuffer().get(v);
        return v;
    }

    /** L2 정규화한 새 배열. null·0벡터면 null */
    static float[] normalize(float[] v) {
        if (v == null || v.length == 0) {
            return null;
        }
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        if (norm == 0 || Double.isNaN(norm)) {
            return null;
        }
        double inv = 1.0 / Math.sqrt(norm);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] * inv);
        }
        return out;
    }

    static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}
