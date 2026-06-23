package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.dto.ChatMessageResponse;
import com.example.demo.entity.dto.ChatMessageSendRequest;
import com.example.demo.service.ChatService;
import com.example.demo.util.PageParamUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/message")
    public SaResult sendMessage(@RequestBody ChatMessageSendRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        ChatMessageResponse response = ChatMessageResponse.from(
                chatService.sendMessage(userId, request.getFriendId(), request.getContent()));
        return SaResult.ok().setData(response);
    }

    @GetMapping("/message")
    public SaResult listMessages(@RequestParam Long friendId,
                                 @RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size) {
        Long userId = StpUtil.getLoginIdAsLong();
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 20, 100);
        return SaResult.ok().setData(chatService.listMessages(userId, friendId, p, s));
    }
}
