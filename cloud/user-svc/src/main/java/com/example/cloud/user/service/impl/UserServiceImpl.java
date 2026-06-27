package com.example.cloud.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.entity.User;
import com.example.cloud.user.repository.UserRepository;
import com.example.cloud.user.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 * <p>
 * 与单体版相比的变化：
 * <ul>
 *   <li>移除 {@code UserVectorService} 调用（属于推荐域，跨服务，后续通过事件 / Feign 接入）</li>
 *   <li>移除 {@code @Cacheable} / {@code @CacheEvict} 注解（{@code CacheConfig} 尚未迁移）</li>
 * </ul>
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    private static final String REGISTER_CODE_KEY_PREFIX = "register:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserRepository userRepository;

    @Override
    public String doLogin(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !Objects.equals(user.getPassword(), password)) {
            throw new BusinessException("用户名或密码错误");
        }
        StpUtil.login(user.getId());
        // TODO 跨服务：登录后通知 push-svc 加载用户兴趣向量到 Redis
        return StpUtil.getTokenValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String registered(String username, String email, String password, String code) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)
                || !StringUtils.hasText(password) || !StringUtils.hasText(code)) {
            throw new BusinessException("用户名、邮箱、密码和验证码均不能为空");
        }
        if (username.trim().length() < 2 || username.trim().length() > 20) {
            throw new BusinessException("用户名长度必须在 2-20 个字符之间");
        }
        String cacheKey = REGISTER_CODE_KEY_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedCode == null) {
            throw new BusinessException("验证码已过期或未发送，请重新获取");
        }
        if (!Objects.equals(code, cachedCode)) {
            throw new BusinessException("验证码不正确");
        }
        stringRedisTemplate.delete(cacheKey);

        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null) {
            throw new BusinessException("用户已存在");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setEmail(email);
        userRepository.save(user);
        StpUtil.login(user.getId());
        // TODO 跨服务：注册后通知 push-svc 初始化用户兴趣向量
        return "注册成功";
    }

    @Override
    public String logout() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("未登录");
        }
        StpUtil.logout();
        // TODO 跨服务：登出后通知 push-svc 清理用户兴趣向量缓存
        return "退出成功";
    }

    @Override
    public String send(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        try {
            String code = String.valueOf(new Random().nextInt(9000) + 1000);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("zilvdexiaozheng@qq.com");
            message.setTo(email);
            message.setSubject("验证码");
            message.setText("您的验证码是:" + code);
            javaMailSender.send(message);
            stringRedisTemplate.opsForValue().set(
                    REGISTER_CODE_KEY_PREFIX + email,
                    code,
                    CODE_EXPIRE_MINUTES,
                    TimeUnit.MINUTES);
            return "发送成功";
        } catch (Exception e) {
            throw new BusinessException("发送失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateUser(User user) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("用户未登录");
        }
        if (user == null) {
            throw new BusinessException("用户信息不能为空");
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        User existing = userRepository.findById(currentUserId).orElse(null);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        boolean hasUpdate = false;
        if (StringUtils.hasText(user.getUsername())) {
            existing.setUsername(user.getUsername().trim());
            hasUpdate = true;
        }
        if (StringUtils.hasText(user.getAvatar())) {
            existing.setAvatar(user.getAvatar().trim());
            hasUpdate = true;
        }
        if (StringUtils.hasText(user.getPassword())) {
            existing.setPassword(user.getPassword());
            hasUpdate = true;
        }
        if (!hasUpdate) {
            throw new BusinessException("没有需要更新的字段");
        }
        userRepository.save(existing);
        return "修改成功";
    }

    @Override
    public User getProfile(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public User findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public User getUserInfo(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean softDelete(Long userId) {
        if (userId == null) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getDeleted())) return false;
        user.setDeleted(true);
        userRepository.save(user);
        log.info("[user] soft delete userId={}", userId);
        return true;
    }
}
