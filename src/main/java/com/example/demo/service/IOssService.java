package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    /**
     * 上传图片文件到 OSS，返回可访问的 URL
     */
    String uploadImage(MultipartFile file);
}
