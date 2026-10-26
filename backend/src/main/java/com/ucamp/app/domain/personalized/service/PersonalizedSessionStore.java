package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.question.GeneratedQuestion;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.service.UserConsentService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 맞춤 질문 회차 저장·조회. LLM 호출 중에는 DB 트랜잭션을 잡지 않도록 저장만 따로 둔다 (RecommendationStore와 같은 방식).
 * 회차를 바꾸는 메서드는 회차 행을 잠그고(PESSIMISTIC_WRITE) 상태를 다시 확인한다.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedSessionStore {
    private static final List<String> UNFINISHED = List.of(AiQuestionSession.IN_PROGRESS, AiQuestionSession.READY);

    private final AiQuestionSessionRepository sessionRepository;
    private final AiQuestionTurnRepository turnRepository;
    private final UserConsentService userConsentService;
    private final TurnCodec codec;

    /** 새 회차 시작 결과. abandoned = 이번에 ABANDONED로 바꾼 이전 회차 수 */
    public record Started(SessionState state, int abandoned) {
    }

    /** 끝나지 않은 이전 회차는 ABANDONED (D17: 다시 시작하면 처음부터). 대상이면 임신 고정 질문(turn 0)을 넣는다 */
    @Transactional
    public Started start(Long userId, boolean askPregnancy) {
        User user = userConsentService.getUser(userId);
        List<AiQuestionSession> unfinished = sessionRepository.findByUser_IdAndStatusIn(userId, UNFINISHED);
        unfinished.forEach(AiQuestionSession::markAbandoned);
        AiQuestionSession session = sessionRepository.save(AiQuestionSession.start(user));
        if (askPregnancy) {
            turnRepository.save(AiQuestionTurn.builder()
                    .session(session).turnNo(0).fixed(true).topic(PregnancyQuestion.TOPIC).depth(0)
                    .questionText(PregnancyQuestion.TEXT).answerType(Topic.SINGLE)
                    .optionsJson(codec.encodeOptions(PregnancyQuestion.OPTIONS))
                    .build());
        }
        return new Started(state(session), unfinished.size());
    }

    @Transactional(readOnly = true)
    public SessionState get(Long userId, Long sessionId) {
        return state(owned(userId, sessionId));
    }

    /** 마지막 질문에 대한 답 저장 (설계 4-3 검증). 고정 질문이면 임신 답을 회차에 저장한다 */
    @Transactional
    public SessionState answer(Long userId, Long sessionId, Long turnId, List<Integer> selected) {
        AiQuestionSession session = lockOwned(userId, sessionId);
        if (!session.isInProgress()) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_STATE);
        }
        AiQuestionTurn turn = turnRepository.findFirstBySession_SessionIdOrderByTurnNoDesc(sessionId)
                .filter(t -> t.getTurnId().equals(turnId) && !t.isAnswered())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSONALIZED_INVALID_ANSWER));
        List<TurnOption> options = codec.decodeOptions(turn.getOptionsJson());
        validate(turn.getAnswerType(), options, selected);
        turn.answer(codec.encodeSelected(selected));
        if (turn.isFixed()) {
            session.answerPregnancy(options.get(selected.get(0)).code());
        }
        return state(session);
    }

    /** 생성한 LLM 질문 추가. 그사이 회차가 끝났으면(그만하기 등) 버리고 empty */
    @Transactional
    public Optional<SessionState> addTurn(Long sessionId, Topic topic, int depth, GeneratedQuestion q) {
        AiQuestionSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSONALIZED_SESSION_NOT_FOUND));
        if (!session.isInProgress()) {
            return Optional.empty();
        }
        int turnNo = turnRepository.findFirstBySession_SessionIdOrderByTurnNoDesc(sessionId)
                .map(t -> t.getTurnNo() + 1).orElse(1);
        turnRepository.save(AiQuestionTurn.builder()
                .session(session).turnNo(Math.max(1, turnNo)).fixed(false).topic(topic.code()).depth(depth)
                .questionText(truncate(q.question(), AiQuestionTurn.QUESTION_LENGTH)).answerType(q.answerType())
                .optionsJson(codec.encodeOptions(q.options().stream()
                        .map(o -> TurnOption.of(o.label(), o.kind())).toList()))
                .llmCallLogId(q.callLogId())
                .build());
        session.countQuestion();
        return Optional.of(state(session));
    }

    /** 더 물을 주제가 없음 → READY. 이미 READY가 아니고 진행 중일 때만 */
    @Transactional
    public SessionState markReady(Long sessionId) {
        AiQuestionSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSONALIZED_SESSION_NOT_FOUND));
        if (session.isInProgress()) {
            session.markReady(false);
        }
        return state(session);
    }

    /**
     * 그만하기 (설계 4-4): 답하지 않은 마지막 질문은 버리고 READY. 답한 질문이 0개여도 된다.
     * 버린 LLM 질문은 question_count에서도 뺀다 (question_count = 저장된 LLM 질문 수). 이미 READY면 그대로 돌려준다
     */
    @Transactional
    public SessionState stop(Long userId, Long sessionId) {
        AiQuestionSession session = lockOwned(userId, sessionId);
        if (session.isReady()) {
            return state(session);
        }
        if (!session.isInProgress()) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_STATE);
        }
        turnRepository.findFirstBySession_SessionIdOrderByTurnNoDesc(sessionId)
                .filter(t -> !t.isAnswered())
                .ifPresent(t -> {
                    if (!t.isFixed()) {
                        session.uncountQuestion();
                    }
                    turnRepository.delete(t);
                });
        turnRepository.flush();
        session.markReady(true);
        return state(session);
    }

    @Transactional
    public void markFailed(Long sessionId, String errorCode) {
        sessionRepository.findForUpdate(sessionId).ifPresent(s -> {
            if (s.isInProgress()) {
                s.markFailed(errorCode);
            }
        });
    }

    // ---------- 내부 ----------

    private AiQuestionSession owned(Long userId, Long sessionId) {
        return sessionRepository.findBySessionIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSONALIZED_SESSION_NOT_FOUND));
    }

    private AiQuestionSession lockOwned(Long userId, Long sessionId) {
        AiQuestionSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSONALIZED_SESSION_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERSONALIZED_SESSION_NOT_FOUND);
        }
        return session;
    }

    // SINGLE 1개, MULTI 1~2개, 범위 안, 중복 없음, NONE/UNKNOWN은 혼자만
    private static void validate(String answerType, List<TurnOption> options, List<Integer> selected) {
        int max = Topic.MULTI.equals(answerType) ? 2 : 1;
        if (selected == null || selected.isEmpty() || selected.size() > max) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_ANSWER);
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer i : selected) {
            if (i == null || i < 0 || i >= options.size() || !seen.add(i)) {
                throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_ANSWER);
            }
        }
        boolean exclusive = selected.stream().map(options::get).map(TurnOption::kind).anyMatch(OptionKind::isExclusive);
        if (exclusive && selected.size() > 1) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_ANSWER);
        }
    }

    private SessionState state(AiQuestionSession s) {
        List<SessionState.TurnState> turns = turnRepository.findBySession_SessionIdOrderByTurnNoAsc(s.getSessionId())
                .stream()
                .map(t -> new SessionState.TurnState(t.getTurnId(), t.getTurnNo(), t.isFixed(), t.getTopic(),
                        t.getDepth(), t.getQuestionText(), t.getAnswerType(), codec.decodeOptions(t.getOptionsJson()),
                        codec.decodeSelected(t.getSelectedJson())))
                .toList();
        return new SessionState(s.getSessionId(), s.getUser().getId(), s.getStatus(), s.getPregnancy(),
                s.getQuestionCount(), s.isStoppedByUser(), s.getRecommendationId(), turns);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
