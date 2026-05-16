package com.example.demo.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.*;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.PostingsRepository;
import com.example.demo.repository.PrimaryCommentRepository;
import com.example.demo.repository.PlanetRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.*;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.*;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员后台控制器
 */
@Api(tags = "后台管理", description = "管理员专用的后台接口")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private IUserService userService;
    @Resource
    private IPostingsService postingsService;
    @Resource
    private IPrimaryCommentService primaryCommentService;
    @Resource
    private ISecondaryCommentService secondaryCommentService;
    @Resource
    private IPlanetService planetService;
    @Resource
    private IReportService reportService;
    @Resource
    private UserRepository userRepository;
    @Resource
    private PostingsRepository postingsRepository;
    @Resource
    private PrimaryCommentRepository primaryCommentRepository;
    @Resource
    private PlanetRepository planetRepository;
    @Resource
    private ReportRepository reportRepository;

    @ApiOperation(value = "获取在线用户列表", notes = "查看所有在线用户的 token 列表")
    @GetMapping("/online-users")
    public SaResult getOnlineUsers() {
        checkAdmin();
        List<String> tokenList = StpUtil.searchTokenValue("", 0, -1, false);
        return SaResult.ok().setData(tokenList);
    }

    @ApiOperation(value = "获取用户列表", notes = "分页获取用户列表，支持关键词搜索")
    @GetMapping("/users")
    public SaResult getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        checkAdmin();

        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<User> pageData = userService.getUserList(keyword, p, s);
        return SaResult.ok().setData(pageData);
    }

    @ApiOperation(value = "获取用户详情", notes = "根据用户 ID 获取用户详细信息")
    @GetMapping("/users/{userId}")
    public SaResult getUserDetail(@PathVariable Long userId) {
        checkAdmin();
        User user = userService.getById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }
        return SaResult.ok().setData(user);
    }

    @ApiOperation(value = "修改用户状态", notes = "禁用或启用用户账号")
    @PutMapping("/users/{userId}/status")
    public SaResult updateUserStatus(
            @PathVariable Long userId,
            @RequestParam String status) {
        checkAdmin();

        User user = userService.getById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }

        user.setRole(status);
        userService.updateById(user);

        return SaResult.ok("用户状态已更新");
    }

    @PostMapping("/kickout")
    public SaResult kickout(@RequestParam Long userId) {
        checkAdmin();

        User user = userService.getById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }

        StpUtil.kickout(userId);

        return SaResult.ok("已将用户 " + user.getUsername() + " 踢下线");
    }

    @PostMapping("/kickout-batch")
    public SaResult kickoutBatch(@RequestBody List<Long> userIds) {
        checkAdmin();

        if (userIds == null || userIds.isEmpty()) {
            return SaResult.error("用户 ID 列表不能为空");
        }

        int count = 0;
        for (Long userId : userIds) {
            try {
                StpUtil.kickout(userId);
                count++;
            } catch (Exception e) {
            }
        }

        return SaResult.ok("成功踢出 " + count + " 个用户");
    }

    @PostMapping("/forceLogout")
    public SaResult forceLogout(@RequestParam Long userId) {
        checkAdmin();

        User user = userService.getById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }

        StpUtil.logout(userId);

        return SaResult.ok("已强制用户 " + user.getUsername() + " 注销");
    }

    @DeleteMapping("/users/{userId}")
    public SaResult deleteUser(@PathVariable Long userId) {
        checkAdmin();

        User user = userService.getById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }

        userRepository.deleteById(userId);
        return SaResult.ok("用户已删除");
    }

    @DeleteMapping("/users/batch")
    public SaResult deleteUsersBatch(@RequestBody List<Long> userIds) {
        checkAdmin();

        if (userIds == null || userIds.isEmpty()) {
            return SaResult.error("用户 ID 列表不能为空");
        }

        userRepository.deleteAllById(userIds);

        return SaResult.ok("批量删除成功");
    }

    @GetMapping("/posts")
    public SaResult getPosts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long planetId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword) {
        checkAdmin();

        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Postings> pageData = postingsService.getPostList(planetId, userId, keyword, p, s);
        return SaResult.ok().setData(pageData);
    }

    @DeleteMapping("/posts/{postId}")
    public SaResult deletePost(@PathVariable Long postId) {
        checkAdmin();
        postingsService.removePost(postId);
        return SaResult.ok("帖子已删除");
    }

    @PutMapping("/posts/{postId}/status")
    public SaResult auditPost(@PathVariable Long postId, @RequestParam Integer status) {
        checkAdmin();
        Postings post = postingsService.getById(postId);
        if (post == null) {
            return SaResult.error("帖子不存在");
        }
        post.setStatus(status);
        post.setUpdateTime(LocalDateTime.now());
        postingsService.updateById(post);
        return SaResult.ok(status == 1 ? "帖子已通过" : "帖子已下架");
    }

    @ApiOperation(value = "审核帖子", notes = "管理员审核帖子，auditStatus: 0-待审核, 1-已通过, 2-已拒绝")
    @PutMapping("/posts/{postId}/audit")
    public SaResult auditPostByAuditStatus(@PathVariable Long postId, @RequestParam Integer auditStatus) {
        checkAdmin();
        Postings post = postingsService.getById(postId);
        if (post == null) {
            return SaResult.error("帖子不存在");
        }
        post.setAuditStatus(auditStatus);
        post.setUpdateTime(LocalDateTime.now());
        postingsService.updateById(post);
        String msg = switch (auditStatus) {
            case 0 -> "设为待审核";
            case 1 -> "审核通过";
            case 2 -> "审核拒绝";
            default -> "状态更新成功";
        };
        return SaResult.ok(msg);
    }

    @ApiOperation(value = "批量审核帖子", notes = "批量设置帖子的审核状态")
    @PutMapping("/posts/batch/audit")
    public SaResult auditPostsBatchByAuditStatus(@RequestBody List<Long> postIds, @RequestParam Integer auditStatus) {
        checkAdmin();
        if (postIds == null || postIds.isEmpty()) {
            return SaResult.error("帖子 ID 列表不能为空");
        }
        int count = 0;
        for (Long postId : postIds) {
            try {
                Postings post = postingsService.getById(postId);
                if (post != null) {
                    post.setAuditStatus(auditStatus);
                    post.setUpdateTime(LocalDateTime.now());
                    postingsService.updateById(post);
                    count++;
                }
            } catch (Exception e) {
            }
        }
        return SaResult.ok("批量审核成功，共处理 " + count + " 个帖子");
    }

    @PutMapping("/posts/batch/status")
    public SaResult auditPostsBatch(@RequestBody List<Long> postIds, @RequestParam Integer status) {
        checkAdmin();
        if (postIds == null || postIds.isEmpty()) {
            return SaResult.error("帖子 ID 列表不能为空");
        }
        for (Long postId : postIds) {
            try {
                Postings post = postingsService.getById(postId);
                if (post != null) {
                    post.setStatus(status);
                    post.setUpdateTime(LocalDateTime.now());
                    postingsService.updateById(post);
                }
            } catch (Exception e) {
            }
        }
        return SaResult.ok(status == 1 ? "批量通过成功" : "批量下架成功");
    }

    @DeleteMapping("/posts/batch")
    public SaResult deletePostsBatch(@RequestBody List<Long> postIds) {
        checkAdmin();

        if (postIds == null || postIds.isEmpty()) {
            return SaResult.error("帖子 ID 列表不能为空");
        }

        for (Long postId : postIds) {
            try {
                postingsService.removePost(postId);
            } catch (Exception e) {
            }
        }

        return SaResult.ok("批量删除成功");
    }

    @GetMapping("/comments")
    public SaResult getComments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Long userId) {
        checkAdmin();

        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<PrimaryComment> pageData = primaryCommentService.getCommentList(postId, userId, p, s);
        return SaResult.ok().setData(pageData);
    }

    @DeleteMapping("/comments/{commentId}")
    public SaResult deleteComment(@PathVariable Long commentId) {
        checkAdmin();
        primaryCommentService.removeComment(commentId);
        return SaResult.ok("评论已删除");
    }

    @GetMapping("/stats/overview")
    public SaResult getStatsOverview() {
        checkAdmin();

        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userRepository.count());
        stats.put("totalPosts", postingsRepository.count());
        stats.put("totalPlanets", planetRepository.count());
        stats.put("totalComments", primaryCommentRepository.count());
        stats.put("totalReports", reportRepository.count());

        return SaResult.ok().setData(stats);
    }

    @ApiOperation(value = "获取当前管理员信息")
    @GetMapping("/info")
    public SaResult getAdminInfo() {
        checkAdmin();
        Long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(currentUserId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }
        return SaResult.ok().setData(user);
    }

    @ApiOperation(value = "获取社区列表")
    @GetMapping("/planets")
    public SaResult getPlanets(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        checkAdmin();

        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Planet> pageData = planetService.getPlanetList(name, category, status, p, s);
        return SaResult.ok().setData(pageData);
    }

    private void checkAdmin() {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(currentUserId);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!isAdmin(user)) {
            throw new RuntimeException("无管理员权限");
        }
    }

    private boolean isAdmin(User user) {
        return "admin".equals(user.getRole());
    }
}
