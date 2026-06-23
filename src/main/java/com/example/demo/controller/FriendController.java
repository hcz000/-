package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.dto.FriendRequestCreateRequest;
import com.example.demo.service.FriendService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/friends")
public class FriendController {

    @Resource
    private FriendService friendService;

    @PostMapping("/request")
    public SaResult requestFriend(@RequestBody FriendRequestCreateRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        friendService.sendFriendRequest(userId, request.getTargetUserId(), request.getMessage());
        return SaResult.ok("好友申请已发送");
    }

    @PostMapping("/request/{requestId}/respond")
    public SaResult respond(@PathVariable Long requestId,
                            @RequestParam boolean accept) {
        Long userId = StpUtil.getLoginIdAsLong();
        friendService.respondFriendRequest(requestId, userId, accept);
        return SaResult.ok(accept ? "已同意好友申请" : "已拒绝好友申请");
    }

    @GetMapping
    public SaResult listFriends() {
        Long userId = StpUtil.getLoginIdAsLong();
        return SaResult.ok().setData(friendService.listFriends(userId));
    }

    @GetMapping("/requests/pending")
    public SaResult pendingRequests() {
        Long userId = StpUtil.getLoginIdAsLong();
        return SaResult.ok().setData(friendService.listPendingRequests(userId));
    }
}
