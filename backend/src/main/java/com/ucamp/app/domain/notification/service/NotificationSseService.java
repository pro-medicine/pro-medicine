package com.ucamp.app.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 실시간 알림(SSE) 연결 관리.
 * - 사용자별로 연결(SseEmitter)을 보관한다. 탭을 여러 개 열면 연결도 여러 개다.
 * - 새 알림이 생기면 "notification" 이벤트로 신호만 보낸다. (알림 내용은 프론트가 기존 API로 다시 조회)
 * - 연결이 끝나거나(시간 초과, 브라우저 종료) 전송에 실패하면 보관소에서 지운다.
 */
@Slf4j
@Service
public class NotificationSseService {

    /** 연결 유지 시간 30분. 끊기면 브라우저(EventSource)가 자동으로 다시 연결한다. */
    static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    static final String EVENT_CONNECT = "connect";
    static final String EVENT_NOTIFICATION = "notification";

    // 여러 요청 스레드가 동시에 넣고 빼므로 동시성 안전한 컬렉션을 사용한다.
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 구독: 연결을 만들어 보관하고, 연결 확인용 "connect" 이벤트를 먼저 보낸다.
     * (첫 이벤트를 바로 보내야 일부 프록시·브라우저에서 응답이 늦게 시작되는 문제를 피할 수 있다.)
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        register(userId, emitter);
        sendTo(userId, emitter, () -> SseEmitter.event().name(EVENT_CONNECT).data("connected"));
        return emitter;
    }

    /** 새 알림 신호. 연결이 없는 사용자면 아무것도 하지 않는다. */
    public void sendNotification(Long userId) {
        send(userId, () -> SseEmitter.event().name(EVENT_NOTIFICATION).data("new"));
    }

    /** 모든 연결에 빈 주석(":heartbeat")을 보내 연결을 유지한다. 프론트에는 이벤트로 전달되지 않는다. */
    public void sendHeartbeat() {
        emitters.keySet().forEach(userId -> send(userId, () -> SseEmitter.event().comment("heartbeat")));
    }

    /** 해당 사용자의 현재 연결 수 (테스트, 로그 확인용) */
    int connectionCount(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list == null ? 0 : list.size();
    }

    /** 연결을 보관하고, 연결이 끝나면 자동으로 지워지도록 콜백을 건다. */
    void register(Long userId, SseEmitter emitter) {
        // compute: 같은 사용자에 대한 추가와 삭제(remove)가 동시에 일어나도 한 번에 하나씩 처리된다.
        // (computeIfAbsent(...).add()는 목록을 꺼낸 뒤 add 사이에 remove가 끼어들면 연결이 누락될 수 있음)
        emitters.compute(userId, (id, list) -> {
            List<SseEmitter> target = (list == null) ? new CopyOnWriteArrayList<>() : list;
            target.add(emitter);
            return target;
        });
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));
    }

    /** 연결 하나를 지운다. 사용자의 연결이 하나도 남지 않으면 사용자 항목도 지운다. */
    void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    private void send(Long userId, Supplier<SseEventBuilder> event) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        list.forEach(emitter -> sendTo(userId, emitter, event));
    }

    // 이벤트 내용은 연결마다 새로 만든다. (같은 builder를 여러 연결에 재사용하지 않기 위해 Supplier 사용)
    private void sendTo(Long userId, SseEmitter emitter, Supplier<SseEventBuilder> event) {
        try {
            emitter.send(event.get());
        } catch (IOException | IllegalStateException e) {
            // 브라우저가 이미 닫혔거나 완료된 연결 → 보관소에서 지운다.
            log.debug("SSE 전송 실패로 연결 제거 userId={}: {}", userId, e.getMessage());
            remove(userId, emitter);
        }
    }
}
