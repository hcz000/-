package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 控制器集成测试类
 * 测试主要的API接口
 * 覆盖模块：帖子、评论、星球、用户、好友、通知、推送、搜索、举报、聊天
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    /**
     * 每个测试方法执行前的初始化
     * 模拟用户登录获取token
     */
    @BeforeEach
    void setUp() {
        // 模拟登录，获取token
        StpUtil.login(1L);
        token = StpUtil.getTokenValue();
    }

    // ==================== 健康检查测试 ====================

    @Test
    @DisplayName("测试健康检查端点")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ==================== 敏感词测试 ====================

    @Test
    @DisplayName("测试获取敏感词列表")
    void testGetSensitiveWordList() throws Exception {
        mockMvc.perform(get("/sensitive/list")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试敏感词检查")
    void testCheckSensitiveWord() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("text", "测试文本");

        mockMvc.perform(post("/sensitive/check")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 推送测试 ====================

    @Test
    @DisplayName("测试获取推送内容")
    void testGetPushContent() throws Exception {
        mockMvc.perform(get("/push/a")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取兴趣推送")
    void testGetInterestPush() throws Exception {
        mockMvc.perform(get("/push/interest")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取随机推送")
    void testGetRandomPush() throws Exception {
        mockMvc.perform(get("/push/random")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 搜索测试 ====================

    @Test
    @DisplayName("测试搜索接口 - 搜索帖子、星球、用户")
    void testSearchAll() throws Exception {
        mockMvc.perform(get("/search/all")
                        .param("keyword", "测试")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试按类型搜索 - 搜索帖子")
    void testSearchByTypePost() throws Exception {
        mockMvc.perform(get("/search/biz/post")
                        .param("keyword", "测试")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试按类型搜索 - 搜索星球")
    void testSearchByTypePlanet() throws Exception {
        mockMvc.perform(get("/search/biz/planet")
                        .param("keyword", "测试")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试按类型搜索 - 搜索用户")
    void testSearchByTypeUser() throws Exception {
        mockMvc.perform(get("/search/biz/user")
                        .param("keyword", "test")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 帖子测试 ====================

    @Test
    @DisplayName("测试获取帖子列表 - 需要planetId参数")
    void testGetPostingsList() throws Exception {
        mockMvc.perform(get("/postings")
                        .param("planetId", "1")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("测试获取单个帖子详情")
    void testGetPostingsById() throws Exception {
        mockMvc.perform(get("/postings/{id}", 2001)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取帖子点赞状态")
    void testGetPostingsLikeStatus() throws Exception {
        mockMvc.perform(get("/postings/{id}/like", 2001)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试搜索帖子")
    void testSearchPostings() throws Exception {
        mockMvc.perform(get("/postings/search")
                        .param("keyword", "测试")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    // ==================== 星球测试 ====================

    @Test
    @DisplayName("测试获取星球列表")
    void testGetPlanetList() throws Exception {
        mockMvc.perform(get("/planet")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取我加入的星球")
    void testGetMyPlanets() throws Exception {
        mockMvc.perform(get("/planet/my")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 评论测试 ====================

    @Test
    @DisplayName("测试获取一级评论列表 - 需要postingsId参数")
    void testGetPrimaryComments() throws Exception {
        mockMvc.perform(get("/primary-comment0")
                        .param("postingsId", "2001")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取一级评论点赞状态")
    void testGetPrimaryCommentLikeStatus() throws Exception {
        mockMvc.perform(get("/primary-comment0/{commentId}/like", 1)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取二级评论列表 - 需要primaryCommentId参数")
    void testGetSecondaryComments() throws Exception {
        mockMvc.perform(get("/secondary-comment0")
                        .param("primaryCommentId", "1")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("测试获取二级评论点赞状态")
    void testGetSecondaryCommentLikeStatus() throws Exception {
        mockMvc.perform(get("/secondary-comment0/{commentId}/like", 1)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 通知测试 ====================

    @Test
    @DisplayName("测试获取通知列表")
    void testGetNotifications() throws Exception {
        mockMvc.perform(get("/t-notification")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取未读通知数量")
    void testGetUnreadNotificationCount() throws Exception {
        mockMvc.perform(get("/t-notification/unread-count")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 好友测试 ====================

    @Test
    @DisplayName("测试获取好友列表")
    void testGetFriends() throws Exception {
        mockMvc.perform(get("/friends")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试获取待处理的好友请求")
    void testGetPendingFriendRequests() throws Exception {
        mockMvc.perform(get("/friends/requests/pending")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 用户测试 ====================

    @Test
    @DisplayName("测试获取当前用户信息")
    void testGetUserProfile() throws Exception {
        mockMvc.perform(get("/user/profile")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("测试通过邮箱搜索用户")
    void testSearchUserByEmail() throws Exception {
        mockMvc.perform(get("/user/search")
                        .param("email", "treenamaradehya@gmail.com")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    // ==================== 报告测试 ====================

    @Test
    @DisplayName("测试获取我的举报列表")
    void testGetMyReports() throws Exception {
        mockMvc.perform(get("/report/my")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 聊天测试 ====================

    @Test
    @DisplayName("测试获取聊天消息列表")
    void testGetChatMessages() throws Exception {
        mockMvc.perform(get("/chat/message")
                        .param("friendId", "2")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }
}
