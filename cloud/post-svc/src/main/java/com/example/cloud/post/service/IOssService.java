package com.example.cloud.post.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    /**
     * 上传图片到对象存储，返回可访问的 URL。
     */
    String uploadImage(MultipartFile file);
}
