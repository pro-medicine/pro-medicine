package com.ucamp.app.domain.friend.controller;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.service.FriendService;
import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(@RequestParam Long userId) {
        List<FriendResponse> responses = friendService.getFriends(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendDetailResponse>> getFriend(
            @RequestParam Long userId,
            @PathVariable Long friendUserId) {
        FriendDetailResponse response = friendService.getFriend(userId, friendUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{friendUserId}/calendar")
    public ResponseEntity<ApiResponse<List<LocalDate>>> getFriendCalendar(
            @RequestParam Long userId,
            @PathVariable Long friendUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(friendService.getFriendCalendar(userId, friendUserId, start, end)));
    }

    @GetMapping("/{friendUserId}/intakes")
    public ResponseEntity<ApiResponse<List<TodayIntakeResponse>>> getFriendIntakes(
            @RequestParam Long userId,
            @PathVariable Long friendUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(friendService.getFriendIntakes(userId, friendUserId, date)));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<Void> deleteFriend(
            @RequestParam Long userId,
            @PathVariable Long friendUserId) {
        friendService.deleteFriend(userId, friendUserId);
        return ResponseEntity.noContent().build();
    }
}
