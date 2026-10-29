
package com.ucamp.app.domain.user.repository;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 닉네임 중복 검사
    boolean existsByNickname(String nickname);

    // 닉네임으로 조회
    Optional<User> findByNickname(String nickname);

    // 회원가입: 이메일 중복 검사
    boolean existsByEmailIgnoreCase(String email);

    // 로그인: 이메일로 사용자 조회
    Optional<User> findByEmailIgnoreCase(String email);

    // 기존 사용자 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);

    @Modifying
    @Query("update User u set u.lastActiveAt = :now " +
            "where u.id = :userId and u.lastActiveAt < :threshold")
    int touchActive(@Param("userId") Long userId,
                    @Param("now")LocalDateTime now,
                    @Param("threshold") LocalDateTime threshold);
}
