package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    /**
     * 自定义的 CDN 或 Bucket 域名,若为空将按 endpoint 拼接。
     */
    private String domain;
    /**
     * 文件在 OSS 上的根目录,默认为 images。
     */
    private String folder = "images";
    
    // ========== 连接池配置 ==========
    /**
     * 最大连接数
     */
    private Integer maxConnections = 1024;
    /**
     * Socket超时时间(ms)
     */
    private Integer socketTimeout = 50000;
    /**
     * 连接超时时间(ms)
     */
    private Integer connectionTimeout = 50000;
    /**
     * 空闲连接超时时间(ms)
     */
    private Integer idleConnectionTimeout = 60000;
    /**
     * 最大重试次数
     */
    private Integer maxErrorRetry = 3;
}
