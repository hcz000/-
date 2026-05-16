package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    /**
     * 上传图片文件到 OSS，返回可访问的 URL
     */
    String uploadImage(MultipartFile file);

    /**
     * 分片上传大文件（大于 5MB 自动分片）
     * @param file 文件
     * @return 文件 URL
     */
    String uploadLargeFile(MultipartFile file);

    /**
     * 大文件阈值（5MB），超过此大小使用分片上传
     */
    long LARGE_FILE_THRESHOLD = 5 * 1024 * 1024;
}
