package com.ucamp.app.domain.friend.service;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public List<FriendResponse> getFriends(Long userId) {
        List<Follow> follows = followRepository.findByFollowerIdAndStatusOrderByFollowIdDesc(userId, FollowStatus.ACCEPTED);
        Map<Long, User> users = userRepository
                .findAllById(follows.stream()
                        .map(Follow::getFollowingId).toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return follows.stream().filter(follow -> users.containsKey(follow.getFollowingId()))
                .map(follow -> FriendResponse.of(follow.getFollowId(), users.get(follow.getFollowingId()))).toList();

    }

    public FriendDetailResponse getFriend(Long userId, Long friendUserId) {
        if (!followRepository.existsByFollowerIdAndFollowingIdAndStatus(userId, friendUserId, FollowStatus.ACCEPTED)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND);
        }
        User friend = userRepository.findById(friendUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return FriendDetailResponse.from(friend);
    }
}