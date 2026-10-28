package com.ucamp.app.domain.friend.service;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final IntakeService intakeService;

    public List<FriendResponse> getFriends(Long userId) {
        List<Follow> follows = followRepository.findByFollowerIdAndStatusOrderByFollowIdDesc(userId, FollowStatus.ACCEPTED);
        Map<Long, User> users = userRepository
                .findAllById(follows.stream()
                        .map(Follow::getFollowingId).toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        // 나를 팔로우하는 사람 -> 서로 팔로우 하는지 판단하기 위해서
        Set<Long> followsMe = followRepository.findByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED).stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toSet());
        return follows.stream()
                .filter(follow -> users.containsKey(follow.getFollowingId()))
                .map(follow -> FriendResponse.of(follow.getFollowId(), users.get(follow.getFollowingId()), followsMe.contains(follow.getFollowingId()))).toList();
    }

    public FriendDetailResponse getFriend(Long userId, Long friendUserId) {
        getAcceptedFollow(userId, friendUserId);
        User friend = getUser(userId);
        return FriendDetailResponse.from(friend);
    }

    public List<LocalDate> getFriendCalendar(Long userId, Long friendUserId, LocalDate start, LocalDate end) {
        getAcceptedFollow(userId, friendUserId);
        User friendUser = getUser(friendUserId);
        // TODO: 친구 캘린터 공개/비공개 설정 기능 생기면 수정
        // if (!friendUser.gS)
            return intakeService.getCompletedDates(friendUserId, start, end);
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        Follow follow = getAcceptedFollow(userId, friendId);
        followRepository.delete(follow);
    }

    private Follow getAcceptedFollow(Long userId, Long friendUserId) {
        return followRepository
                .findByFollowerIdAndFollowingIdAndStatus(userId, friendUserId, FollowStatus.ACCEPTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FRIEND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}