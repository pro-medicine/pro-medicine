package com.ucamp.app.domain.follow.service;

import com.ucamp.app.domain.follow.dto.FollowResponse;
import com.ucamp.app.domain.follow.entity.Follow;
import com.ucamp.app.domain.follow.enums.FollowStatus;
import com.ucamp.app.domain.follow.repository.FollowRepository;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    // TODO: User 정보 가져오기

    @Transactional
    public FollowResponse request(Long followerId, String nickname) {
        Long followingId = findUserIdByNickname(nickname);

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }

        try {
            Follow saved = followRepository.saveAndFlush(Follow.request(followerId, followingId));
            // TODO: 상대(followingId)에게 알림
            return FollowResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }
    }

    public List<FollowResponse> getRequests(Long userId, String direction, FollowStatus status) {
        List<Follow> follows = switch (direction) {
            case "received" -> followRepository.findByFollowingIdAndStatusOrderByFollowIdDesc(userId, status);
            case "sent" -> followRepository.findByFollowerIdAndStatusOrderByFollowIdDesc(userId, status);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        return follows.stream().map(FollowResponse::from).toList();
    }

    @Transactional
    public FollowResponse respond(Long userId, Long followId, FollowStatus status) {
        Follow follow = getFollow(followId);

        if (!follow.getFollowingId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        try {
            switch (status) {
                case ACCEPTED -> follow.accept();
                case REJECTED -> follow.reject();
                default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMPLETED_FOLLOW_REQUEST);
        }

        // TODO: 요청한 사람에게 알림
        return FollowResponse.from(follow);
    }

    @Transactional
    public void cancel(Long userId, Long followId) {
        Follow follow = getFollow(followId);

        boolean isParty = follow.getFollowerId().equals(userId)
                || follow.getFollowingId().equals(userId);
        if (!isParty) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // TODO: 데이터 조회 시 deletedAt Null 만 하도록 수정
        follow.markDeleted(userId);
    }

    // TODO: User 생기면 교체 필요
    private Long findUserIdByNickname(String nickname) {
        try {
            return Long.parseLong(nickname);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Follow getFollow(Long followId) {
        return followRepository.findById(followId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOWING_NOT_FOUND));
    }
}