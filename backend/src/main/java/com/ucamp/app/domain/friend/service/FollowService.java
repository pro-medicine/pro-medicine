package com.ucamp.app.domain.friend.service;

import com.ucamp.app.domain.friend.dto.FollowResponse;
import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ucamp.app.domain.notification.event.FollowAcceptedEvent;
import com.ucamp.app.domain.notification.event.FollowRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FollowResponse request(Long followerId, String nickname) {
        Long followingId = userRepository.findByNickname(nickname.strip())
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }

        try {
            Follow saved = followRepository.saveAndFlush(Follow.request(followerId, followingId));
            eventPublisher.publishEvent(new FollowRequestedEvent(followerId, followingId));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }
    }

    public List<FollowResponse> getRequests(Long userId, String direction, FollowStatus status) {
        List<Follow> follows = switch (direction) {
            case "received" -> followRepository.findByFollowingIdAndStatusAndDeletedAtIsNullOrderByFollowIdDesc(userId, status);
            case "sent" -> followRepository.findByFollowerIdAndStatusAndDeletedAtIsNullOrderByFollowIdDesc(userId, status);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        return toResponses(follows);
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

        if (status == FollowStatus.ACCEPTED) {
            eventPublisher.publishEvent(
                    new FollowAcceptedEvent(follow.getFollowerId(), follow.getFollowingId()));
        }
        return toResponse(follow);
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

    private Follow getFollow(Long followId) {
        return followRepository.findById(followId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOWING_NOT_FOUND));
    }

    private FollowResponse toResponse(Follow follow) {
        return toResponses(List.of(follow)).getFirst();
    }

    private List<FollowResponse> toResponses(List<Follow> follows) {
        Set<Long> userIds = new HashSet<>();
        follows.forEach(f -> {
            userIds.add(f.getFollowerId());
            userIds.add(f.getFollowingId());
        });
        Map<Long, String> nicknames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        return follows.stream()
                .map(f -> FollowResponse.of(
                        f,
                        nicknames.get(f.getFollowerId()),
                        nicknames.get(f.getFollowingId())))
                .toList();
    }
}