package com.example.cloud.push.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.push.entity.Postings;
import com.example.cloud.push.service.AuthorEnrichService;
import com.example.cloud.push.service.PushService;
import com.example.cloud.push.service.SeenPostService;
import com.example.cloud.push.vo.PostWithAuthorVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/push")
public class PushController {

    @Resource
    private PushService pushService;

    @Resource
    private SeenPostService seenPostService;

    @Resource
    private AuthorEnrichService authorEnrichService;

    @GetMapping("/a")
    public SaResult push(@RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data = pushService.pushForCurrentUser(exclude);
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    @GetMapping("/interest")
    public SaResult interestPush(@RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data = pushService.likepush(exclude);
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    @GetMapping("/random")
    public SaResult randomPush(@RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data = pushService.push(exclude);
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    @GetMapping("/hot")
    public SaResult hotPush(@RequestParam(required = false) Integer size,
                            @RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data = pushService.hotPush(size == null ? 10 : size, exclude);
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    @GetMapping("/planet")
    public SaResult planetPush(@RequestParam(required = false) Integer size,
                               @RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data;
        if (!StpUtil.isLogin()) {
            data = pushService.push(exclude);
        } else {
            data = pushService.planetPush(StpUtil.getLoginIdAsLong(), size == null ? 10 : size, exclude);
        }
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    @GetMapping("/friends")
    public SaResult friendPush(@RequestParam(required = false) Integer size,
                               @RequestParam(required = false) String excludeIds) {
        Set<Long> exclude = resolveExcludeIds(excludeIds);
        List<Postings> data;
        if (!StpUtil.isLogin()) {
            data = pushService.push(exclude);
        } else {
            data = pushService.friendPush(StpUtil.getLoginIdAsLong(), size == null ? 10 : size, exclude);
        }
        markSeenIfLogin(data);
        return SaResult.ok().setData(authorEnrichService.enrich(data));
    }

    /**
     * 演示用：直接调用一次 Feign 看打通了没。
     * 实际生产代码里这种探针接口应该挂在 actuator 下，这里图方便。
     */
    @GetMapping("/_ping/user-svc")
    public SaResult pingUserSvc(@RequestParam Long userId) {
        List<Postings> fake = new ArrayList<>();
        Postings p = new Postings();
        p.setPostingsId(0L);
        p.setUserId(userId);
        fake.add(p);
        List<PostWithAuthorVO> enriched = authorEnrichService.enrich(fake);
        return SaResult.ok().setData(enriched.isEmpty() ? null : enriched.get(0).getAuthor());
    }

    private Set<Long> resolveExcludeIds(String excludeIdsParam) {
        Set<Long> result = parseCsv(excludeIdsParam);
        if (StpUtil.isLogin()) {
            Set<Long> server = seenPostService.getSeen(StpUtil.getLoginIdAsLong());
            if (!server.isEmpty()) {
                result.addAll(server);
            }
        }
        return result;
    }

    private Set<Long> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        String[] parts = csv.split(",");
        Set<Long> result = new HashSet<>(parts.length);
        for (String p : parts) {
            String s = p == null ? null : p.trim();
            if (s == null || s.isEmpty()) continue;
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private void markSeenIfLogin(List<Postings> data) {
        if (data == null || data.isEmpty() || !StpUtil.isLogin()) {
            return;
        }
        List<Long> ids = new ArrayList<>(data.size());
        for (Postings p : data) {
            if (p != null && p.getPostingsId() != null) {
                ids.add(p.getPostingsId());
            }
        }
        if (ids.isEmpty()) return;
        seenPostService.markSeen(StpUtil.getLoginIdAsLong(),
                ids.stream().filter(Objects::nonNull).distinct().toList());
    }
}
