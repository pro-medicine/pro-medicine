package com.ucamp.app.domain.user.service;

import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.domain.user.security.PasswordHasher;
import com.ucamp.app.domain.user.security.RefreshTokenService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountWithdrawalService {
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final JdbcTemplate jdbc;
    private final RefreshTokenService refreshTokens;

    @Transactional
    public void withdraw(Long userId, String password) {
        User user = users.findWithLockById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (password == null || !passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        List<Long> recommendationIds = jdbc.queryForList("""
                SELECT DISTINCT r.recommendation_id
                FROM ai_recommendations r
                LEFT JOIN survey_responses sr ON sr.response_id = r.response_id
                LEFT JOIN ai_question_sessions qs ON qs.session_id = r.session_id
                LEFT JOIN ai_chat_sessions cs ON cs.chat_session_id = r.chat_session_id
                LEFT JOIN ai_chat_results cr ON cr.recommendation_id = r.recommendation_id
                LEFT JOIN ai_chat_sessions crs ON crs.chat_session_id = cr.chat_session_id
                WHERE r.user_id = ? OR sr.user_id = ? OR qs.user_id = ?
                   OR cs.user_id = ? OR cr.user_id = ? OR crs.user_id = ?
                """, Long.class, userId, userId, userId, userId, userId, userId);

        jdbc.update("""
                DELETE FROM llm_call_logs
                WHERE response_id IN (SELECT response_id FROM survey_responses WHERE user_id = ?)
                   OR session_id IN (SELECT session_id FROM ai_question_sessions WHERE user_id = ?)
                """, userId, userId);

        for (int from = 0; from < recommendationIds.size(); from += 500) {
            List<Long> ids = new ArrayList<>(recommendationIds.subList(
                    from, Math.min(from + 500, recommendationIds.size())));
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            jdbc.update("DELETE FROM ai_recommendation_products WHERE recommendation_item_id IN "
                    + "(SELECT recommendation_item_id FROM ai_recommendation_items WHERE recommendation_id IN ("
                    + placeholders + "))", ids.toArray());
            jdbc.update("DELETE FROM ai_recommendation_items WHERE recommendation_id IN ("
                    + placeholders + ")", ids.toArray());
            jdbc.update("DELETE FROM ai_recommendations WHERE recommendation_id IN ("
                    + placeholders + ")", ids.toArray());
        }

        jdbc.update("""
                DELETE FROM ai_chat_results WHERE user_id = ?
                   OR chat_session_id IN (SELECT chat_session_id FROM ai_chat_sessions WHERE user_id = ?)
                """, userId, userId);
        jdbc.update("""
                DELETE FROM ai_chat_turns WHERE chat_session_id IN
                  (SELECT chat_session_id FROM ai_chat_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM ai_chat_sessions WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM ai_question_turns WHERE session_id IN
                  (SELECT session_id FROM ai_question_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM ai_question_sessions WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM survey_answers WHERE response_id IN
                  (SELECT response_id FROM survey_responses WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM survey_responses WHERE user_id = ?", userId);

        jdbc.update("DELETE FROM notifications WHERE user_id = ? OR actor_user_id = ?", userId, userId);
        jdbc.update("DELETE FROM guardian_settings WHERE user_id = ? OR guardian_user_id = ?", userId, userId);
        jdbc.update("DELETE FROM follows WHERE follower_id = ? OR following_id = ?", userId, userId);
        jdbc.update("""
                DELETE FROM intake_records WHERE schedule_id IN (
                    SELECT s.schedule_id FROM intake_schedules s
                    JOIN user_products p ON p.user_product_id = s.user_product_id
                    WHERE p.user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM intake_schedules WHERE user_product_id IN
                  (SELECT user_product_id FROM user_products WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM user_products WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM calendar_events WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM prescriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_medications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_conditions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM health_profiles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth_demographics WHERE user_id = ?", userId);

        users.delete(user);
        users.flush();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refreshTokens.revokeAllForUser(userId);
                } catch (RuntimeException ex) {
                    // Existing JWTs and refresh requests are rejected because the user no longer exists.
                    log.warn("회원 탈퇴 후 Redis 세션 정리 실패: userId={}", userId, ex);
                }
            }
        });
    }
}
