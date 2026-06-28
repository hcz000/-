package com.example.cloud.post.service.impl;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.service.IOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * OSS 服务（简化版：写本地磁盘）。
 * <p>
 * 单体里的实现是 302 行的 Aliyun OSS SDK 集成（分片上传、连接池、CDN 域名等），
 * 因为对外暴露需要真实的 ak/sk + bucket，本演示项目把它降级为「写本地磁盘 + 用 /static 路径访问」。
 * <p>
 * 升级到真实 OSS 时：替换 {@link #uploadImage} 内部实现为 OSSClient.putObject，
 * 把 endpoint / bucket / ak / sk 从 application.yml 的 aliyun.oss.* 读取即可。
 */
@Slf4j
@Service
public class OssServiceImpl implements IOssService {

    /** 允许的 content-type */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/svg+xml");

    /** 5MB 大小上限 */
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Value("${app.oss.local-dir:${user.home}/.knowledgeplanet/uploads}")
    private String localDir;

    @Value("${app.oss.public-base-url:http://localhost:8082/static/uploads}")
    private String publicBaseUrl;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("文件为空");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("文件超过 5MB");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持图片格式：" + ALLOWED_TYPES);
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = inferExtension(file.getOriginalFilename(), contentType);
        String objectName = today + "/" + UUID.randomUUID() + ext;

        Path dest = Paths.get(localDir, objectName);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            String url = publicBaseUrl.endsWith("/")
                    ? publicBaseUrl + objectName
                    : publicBaseUrl + "/" + objectName;
            log.info("[oss] uploaded {} → {}", file.getOriginalFilename(), url);
            return url;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage(), e);
        }
    }

    private String inferExtension(String filename, String contentType) {
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> "";
        };
    }
}
