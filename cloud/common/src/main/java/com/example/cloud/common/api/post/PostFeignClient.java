package com.example.cloud.common.api.post;

import com.example.cloud.common.constant.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * post-svc 的内部接口 Feign 客户端。
 * <p>
 * 当前只暴露注销级联需要的删帖接口；后续 user 标记被禁言、
 * 内容审核拒绝时，可以扩展更多操作。
 */
@FeignClient(name = ServiceNames.POST_SVC, contextId = "postFeignClient")
public interface PostFeignClient {

    /**
     * 软删除某个用户的所有帖子。
     *
     * @return 受影响的帖子数
     */
    @DeleteMapping("/internal/post/by-user/{userId}")
    Integer deletePostsByUser(@PathVariable("userId") Long userId);
}
