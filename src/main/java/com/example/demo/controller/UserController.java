package com.example.demo.controller;


import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.IUserService;
import com.example.demo.service.IOssService;
import io.swagger.annotations.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器
 * 提供用户登录、注册、登出等功能
 */
@Api(tags = "用户管理", description = "提供用户登录、注册、登出、个人信息等接口")
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    @Resource
    private IOssService ossService;

    @ApiOperation(value = "用户登录", notes = "使用邮箱和密码登录")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "email", value = "用户邮箱", required = true, dataType = "String", paramType = "query", example = "user@example.com"),
        @ApiImplicitParam(name = "password", value = "用户密码", required = true, dataType = "String", paramType = "query", example = "123456")
    })
    @ApiResponses({
        @ApiResponse(code = 200, message = "登录成功"),
        @ApiResponse(code = 400, message = "邮箱或密码错误"),
        @ApiResponse(code = 401, message = "登录失败")
    })
    @RequestMapping("/Login")
    public SaResult doLogin(@RequestParam @ApiParam(value = "用户邮箱", required = true) String email, @RequestParam @ApiParam(value = "用户密码", required = true) String password) {
        String token = userService.doLogin(email, password);
        return SaResult.ok().setData(token);
    }

    @ApiOperation(value = "用户注册", notes = "使用邮箱、密码和验证码注册新用户")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "username", value = "用户名", required = true, dataType = "String", paramType = "query", example = "张三"),
        @ApiImplicitParam(name = "email", value = "用户邮箱", required = true, dataType = "String", paramType = "query", example = "user@example.com"),
        @ApiImplicitParam(name = "password", value = "用户密码", required = true, dataType = "String", paramType = "query", example = "123456"),
        @ApiImplicitParam(name = "code", value = "验证码", required = true, dataType = "String", paramType = "query", example = "123456")
    })
    @ApiResponses({
        @ApiResponse(code = 200, message = "注册成功"),
        @ApiResponse(code = 400, message = "邮箱已存在或验证码错误"),
        @ApiResponse(code = 401, message = "注册失败")
    })
    @RequestMapping("/registered")
    public SaResult registered(@RequestParam @ApiParam(value = "用户名", required = true) String username, @RequestParam @ApiParam(value = "用户邮箱", required = true) String email, @RequestParam @ApiParam(value = "用户密码", required = true) String password, @RequestParam @ApiParam(value = "验证码", required = true) String code) {
        return SaResult.ok(userService.registered(username, email, password, code));
    }

    @ApiOperation(value = "用户登出", notes = "退出当前登录")
    @ApiResponses({
        @ApiResponse(code = 200, message = "登出成功"),
        @ApiResponse(code = 401, message = "未登录")
    })
    @RequestMapping("/logout")
    public SaResult logout() {
        // 未登录时也允许调用 logout，不报错
        if (!StpUtil.isLogin()) {
            return SaResult.ok();
        }
        return SaResult.ok(userService.logout());
    }

    @RequestMapping("/send")
    public SaResult send(@RequestParam String email) {
        return SaResult.ok(userService.send(email));
    }

    @PostMapping("/updatauser")
    public SaResult updataUser(@RequestBody User user) {
        // 更新用户信息需要登录
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        return SaResult.ok(userService.updataUser(user));
    }

    @GetMapping("/profile")
    public SaResult profile() {
        // 获取用户信息 - 未登录时返回 null，不报错
        // 前端会根据返回值判断用户是否登录
        if (!StpUtil.isLogin()) {
            return SaResult.ok().setData(null);
        }
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return SaResult.ok().setData(userService.getProfile(userId));
        } catch (BusinessException e) {
            // 用户已被删除，清除无效 token
            StpUtil.logout();
            return SaResult.ok().setData(null);
        }
    }

    @PostMapping("/avatar")
    public SaResult uploadAvatar(@RequestPart("file") MultipartFile file) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        String url = ossService.uploadImage(file);
        // 更新用户头像
        Long userId = StpUtil.getLoginIdAsLong();
        User user = new User();
        user.setId(userId);
        user.setAvatar(url);
        userService.updataUser(user);
        return SaResult.ok().setData(url);
    }

    @ApiOperation(value = "通过邮箱查找用户", notes = "搜索用户，用于邀请好友")
    @GetMapping("/search")
    public SaResult searchByEmail(@RequestParam String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            return SaResult.error("用户不存在").setCode(404);
        }
        return SaResult.ok().setData(user);
    }

    @ApiOperation(value = "获取用户信息", notes = "获取指定用户的基本信息")
    @GetMapping("/info/{userId}")
    public SaResult getUserInfo(@PathVariable Long userId) {
        User user = userService.getUserInfo(userId);
        if (user == null) {
            return SaResult.error("用户不存在").setCode(404);
        }
        return SaResult.ok().setData(user);
    }
}