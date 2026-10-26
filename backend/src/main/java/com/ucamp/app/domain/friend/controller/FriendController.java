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
        return ResponseEntity.ok(ApiResponse.success(friendService.getFriends(userId)));
    }

    @GetMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendDetailResponse>> getFriend(
            @RequestParam Long userId,
            @PathVariable Long friendUserId) {
        return ResponseEntity.ok(ApiResponse.success(friendService.getFriend(userId, friendUserId)));
    }
}
