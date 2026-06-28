package com.example.cloud.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.util.PageParamUtil;
import com.example.cloud.user.dto.ChatMessageResponse;
import com.example.cloud.user.dto.ChatMessageSendRequest;
import com.example.cloud.user.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/message")
    public SaResult sendMessage(@RequestBody ChatMessageSendRequest request) {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        Long userId = StpUtil.getLoginIdAsLong();
        ChatMessageResponse response = ChatMessageResponse.from(
                chatService.sendMessage(userId, request.getFriendId(), request.getContent()));
        return SaResult.ok().setData(response);
    }

    @GetMapping("/message")
    public SaResult listMessages(@RequestParam Long friendId,
                                 @RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size) {
        if (!StpUtil.isLogin()) return SaResult.error("未登录").setCode(401);
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 20, 100);
        return SaResult.ok().setData(chatService.listMessages(userId, friendId, p, s));
    }
}
