
package com.ucamp.app.domain.friend.controller;

import com.ucamp.app.domain.friend.dto.FriendDetailResponse;
import com.ucamp.app.domain.friend.dto.FriendResponse;
import com.ucamp.app.domain.friend.service.FriendService;
import com.ucamp.app.global.auth.CurrentUserId;
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

    // 로그인한 사용자의 친구 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(
            @CurrentUserId Long userId
    ) {
        List<FriendResponse> responses = friendService.getFriends(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // 로그인한 사용자의 친구 상세 조회
    @GetMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<FriendDetailResponse>> getFriend(
            @CurrentUserId Long userId,
            @PathVariable Long friendUserId
    ) {
        FriendDetailResponse response =
                friendService.getFriend(userId, friendUserId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 로그인한 사용자의 친구 삭제
    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> deleteFriend(
            @CurrentUserId Long userId,
            @PathVariable Long friendUserId
    ) {
        friendService.deleteFriend(userId, friendUserId);
        return ResponseEntity.noContent().build();
    }
}
