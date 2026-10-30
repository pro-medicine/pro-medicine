package com.ucamp.app.domain.friend.service;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.service.MySupplementService;
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
    private final MySupplementService mySupplementService;


    public List<FriendResponse> getFriends(Long userId) {
        List<Follow> follows = followRepository
                .findByFollowerIdAndStatusOrderByFollowIdDesc(
                        userId, FollowStatus.ACCEPTED);
        List<Long> friendIds = follows.stream()
                .map(Follow::getFollowingId)
                .toList();
        Map<Long, User> users = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Integer> streaks = intakeService.getStreakDays(
                users.values().stream()
                        .filter(User::isShareIntakeStatus)
                        .map(User::getId)
                        .collect(Collectors.toSet())
        );
        return follows.stream()
                .filter(f -> users.containsKey(f.getFollowingId()))
                .map(f -> {
                    Long friendId = f.getFollowingId();
                    boolean mutual = followRepository.existsByFollowerIdAndFollowingIdAndStatus(friendId, userId, FollowStatus.ACCEPTED);
                    return FriendResponse.of(f.getFollowId(), users.get(friendId), mutual, streaks.getOrDefault(friendId, 0)
                    );
                })
                .toList();
    }

    public FriendDetailResponse getFriend(Long userId, Long friendUserId) {
        getAcceptedFollow(userId, friendUserId);
        User friend = getUser(friendUserId);
        if (!friend.isShareIntakeStatus()) return FriendDetailResponse.hidden(friend);
        IntakeStatsResponse stats = intakeService.getStats(friendUserId);
        List<MySupplementResponse> supplements = mySupplementService.getMyActiveSupplements(friendUserId);
        return FriendDetailResponse.of(friend, stats, supplements);
    }

    public List<LocalDate> getFriendCalendar(Long userId, Long friendUserId, LocalDate start, LocalDate end) {
        getAcceptedFollow(userId, friendUserId);
        requireShared(getUser(friendUserId));
        return intakeService.getCompletedDates(friendUserId, start, end);
    }

    public List<TodayIntakeResponse> getFriendIntakes(Long userId, Long friendUserId, LocalDate date) {
        getAcceptedFollow(userId, friendUserId);
        requireShared(getUser(friendUserId));
        return intakeService.getTodayIntakes(friendUserId, date);
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

    private void requireShared(User friend) {
        if (!friend.isShareIntakeStatus()) {
            throw new BusinessException(ErrorCode.INTAKE_NOT_SHARED);
        }
    }
}