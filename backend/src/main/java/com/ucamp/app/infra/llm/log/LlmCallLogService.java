package com.ucamp.app.infra.llm.log;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 호출 기록은 바깥 트랜잭션이 롤백돼도 남아야 해서 항상 새 트랜잭션으로 쓴다
@Service
@RequiredArgsConstructor
public class LlmCallLogService {
    private final LlmCallLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long save(LlmCallLog log) {
        return repository.save(log).getLlmCallLogId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutcome(Long callLogId, String outcome) {
        if (callLogId == null) {
            return;
        }
        repository.findById(callLogId).ifPresent(log -> log.changeOutcome(outcome));
    }
}
