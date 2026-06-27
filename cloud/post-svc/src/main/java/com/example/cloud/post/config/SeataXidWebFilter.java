package com.example.cloud.post.config;

import io.seata.core.context.RootContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从入站 HTTP 请求头读取 Seata xid，绑定到当前线程的 {@link RootContext}。
 * <p>
 * 这样 post-svc 内部的本地 UPDATE 会被识别为全局事务的一个分支，
 * 写 undo_log，参与全局提交/回滚。
 * <p>
 * Spring Cloud Alibaba 的 seata starter 默认提供这个能力，
 * 我们用纯 seata 客户端所以手写一个 servlet filter。
 */
@Slf4j
@Component
public class SeataXidWebFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String xid = request.getHeader(RootContext.KEY_XID);
        boolean bound = false;
        if (xid != null && !xid.isBlank() && RootContext.getXID() == null) {
            RootContext.bind(xid);
            bound = true;
            log.debug("[seata-filter] bind xid={} from header", xid);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (bound) {
                String unbind = RootContext.unbind();
                log.debug("[seata-filter] unbind xid={}", unbind);
            }
        }
    }
}
