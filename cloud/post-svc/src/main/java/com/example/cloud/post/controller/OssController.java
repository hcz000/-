package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.post.service.IOssService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/oss")
public class OssController {

    @Resource
    private IOssService ossService;

    @PostMapping("/upload")
    public SaResult upload(@RequestPart("file") MultipartFile file,
                           @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null || xUserId.isBlank()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        return SaResult.ok().setData(ossService.uploadImage(file));
    }
}
