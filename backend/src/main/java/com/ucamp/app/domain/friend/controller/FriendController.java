package com.ucamp.app.domain.friend.controller;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.service.FriendService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> deleteFriend(
            @RequestParam Long userId,
            @PathVariable Long friendUserId) {
        friendService.deleteFriend(userId, friendUserId);
        return ResponseEntity.noContent().build();
    }
}
