package com.ucamp.app.domain.friend.service;

import com.ucamp.app.domain.friend.dto.FollowResponse;
import com.ucamp.app.domain.friend.dto.FollowerResponse;
import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.guardian.service.GuardianService;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GuardianService guardianService;

    @Transactional
    public FollowResponse request(Long followerId, String nickname) {
        Long followingId = userRepository.findByNickname(nickname.strip())
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW);
        }

        // 기존 신청 확인
        Follow existing = followRepository
                .findByFollowerIdAndFollowingId(followerId, followingId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == FollowStatus.REJECTED) {
                throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
            }
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

    public List<FollowResponse> getRequests(
            Long userId,
            String direction,
            FollowStatus status
    ) {
        List<Follow> follows = switch (direction) {
            case "received" -> followRepository.findByFollowingIdAndStatusOrderByFollowIdDesc(userId, FollowStatus.PENDING);
            case "sent" -> followRepository.findByFollowerIdAndStatusInOrderByFollowIdDesc(userId, List.of(FollowStatus.PENDING, FollowStatus.REJECTED));
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        List<FollowResponse> responses = toResponses(follows);
        if (!direction.equals("sent")) {
            return responses;
        }
        return responses.stream()
                .map(r -> r.status() == FollowStatus.REJECTED
                ? new FollowResponse(r.followId(), r.followerId(), r.followerNickname(), r.followingId(), r.followingNickname(), FollowStatus.PENDING) : r).toList();
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

    public List<FollowerResponse> getFollowers(Long userId) {
        List<Follow> incoming = followRepository.findByFollowingIdAndStatusOrderByFollowIdDesc(userId, FollowStatus.ACCEPTED);
        if (incoming.isEmpty()) return List.of();
        List<Long> followerIds = incoming.stream().map(Follow::getFollowerId).toList();
        Map<Long, Follow> outgoing = followRepository
                .findByFollowerIdAndFollowingIdIn(userId, followerIds).stream()
                .collect(Collectors.toMap(Follow::getFollowingId, Function.identity()));

        Map<Long, User> users = userRepository.findAllById(followerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return incoming.stream()
                .filter(f -> users.containsKey(f.getFollowerId()))
                .filter(f -> {
                    Follow mine = outgoing.get(f.getFollowerId());
                    return mine == null || mine.getStatus() != FollowStatus.ACCEPTED;
                })
                .map(f -> {
                    Follow mine = outgoing.get(f.getFollowerId());
                    FollowStatus followBack = mine == null ? null : (mine.getStatus() == FollowStatus.REJECTED ? FollowStatus.PENDING : mine.getStatus());
                    return FollowerResponse.of(f.getFollowId(), users.get(f.getFollowerId()), followBack);
                }).toList();
    }

    @Transactional
    public void cancel(Long userId, Long followId) {
        Follow follow = followRepository.findByFollowIdForUpdate(followId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOWING_NOT_FOUND));
        boolean isParty = follow.getFollowerId().equals(userId)
                || follow.getFollowingId().equals(userId);
        if (!isParty) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (guardianService.existsBetween(
                follow.getFollowerId(),
                follow.getFollowingId())) {
            throw new BusinessException(ErrorCode.GUARDIAN_EXISTS);
        }
        followRepository.delete(follow);
    }

    private Follow getFollow(Long followId) {
        return followRepository.findByFollowId(followId)
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