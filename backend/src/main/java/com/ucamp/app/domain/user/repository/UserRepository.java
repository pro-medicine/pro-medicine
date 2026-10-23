package com.ucamp.app.domain.user.repository;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByNickname(String nickname);
    Optional<User> findByNickname(String nickname);

    // findById와 같지만 유저를 잠가서 조회한다.
    // 같은 유저의 요청이 동시에 오면, 먼저 온 요청이 끝날 때까지 다음 요청은 기다린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);
}
