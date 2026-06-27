package com.example.cloud.push.vo;

import com.example.cloud.common.api.user.UserInfoDTO;
import com.example.cloud.push.entity.Postings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 推送返回值：帖子 + 作者脱敏信息。
 * <p>
 * Postings 自身只有 userId，前端展示需要作者昵称、头像，
 * 通过 Feign 调 user-svc 拿到作者信息后组装成这个 VO。
 * <p>
 * 当 user-svc 不可用时，{@link #author} 为 null，前端按缺省策略渲染。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostWithAuthorVO implements Serializable {

    private Postings post;
    private UserInfoDTO author;

    public static PostWithAuthorVO of(Postings post, UserInfoDTO author) {
        return new PostWithAuthorVO(post, author);
    }
}
