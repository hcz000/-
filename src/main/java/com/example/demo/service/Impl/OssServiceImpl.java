package com.example.demo.service.Impl;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.example.demo.config.OssProperties;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.IOssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class OssServiceImpl implements IOssService {

    private static final long PART_SIZE = 1024 * 1024;
    private static final long LARGE_FILE_THRESHOLD = 5 * 1024 * 1024;
    
    // 允许上传的文件类型
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
        "image/svg+xml", "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    // 允许的文件扩展名
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "pdf", "doc", "docx"
    );

    @Resource
    private OssProperties ossProperties;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;
    
    // OSS客户端复用(线程安全)
    private OSS ossClient;
    
    @PostConstruct
    public void init() {
        ossClient = createOssClient();
        log.info("OSS客户端初始化完成");
    }
    
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS客户端已关闭");
        }
    }

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        
        // 验证文件类型
        validateFileType(file);
        
        // 验证文件大小
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过20MB");
        }
        
        if (file.getSize() > LARGE_FILE_THRESHOLD) {
            return uploadLargeFile(file);
        }
        return uploadSimple(file);
    }
    
    /**
     * 验证文件类型
     */
    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("不支持的文件类型: " + contentType);
        }
        
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("不支持的文件扩展名: " + extension);
        }
    }

    public String uploadLargeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String objectName = buildObjectName(file.getOriginalFilename());
        String uploadId = null;

        try {
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(
                    ossProperties.getBucketName(), objectName);
            if (StringUtils.hasText(file.getContentType())) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(file.getContentType());
                initRequest.setObjectMetadata(metadata);
            }

            InitiateMultipartUploadResult initResult = ossClient.initiateMultipartUpload(initRequest);
            uploadId = initResult.getUploadId();

            long fileSize = file.getSize();
            int partCount = (int) (fileSize / PART_SIZE);
            if (fileSize % PART_SIZE != 0) {
                partCount++;
            }

            log.info("开始分片上传,文件名: {}, 大小: {}MB, 分片数: {}",
                    file.getOriginalFilename(), fileSize / 1024 / 1024, partCount);

            List<PartETag> partETags = uploadPartsParallel(
                    file, objectName, uploadId, partCount, fileSize);

            CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(
                    ossProperties.getBucketName(), objectName, uploadId, partETags);
            ossClient.completeMultipartUpload(completeRequest);

            log.info("分片上传完成: {}", buildFileUrl(objectName));
            return buildFileUrl(objectName);
        } catch (Exception e) {
            log.error("分片上传失败: {}", file.getOriginalFilename(), e);
            // 显式 abort 已传分片，避免孤儿分片占用 OSS 存储
            // （另外建议桶层面配 N 天生命周期规则做兜底，防 abort 本身失败）
            if (uploadId != null) {
                try {
                    ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(
                            ossProperties.getBucketName(), objectName, uploadId));
                    log.info("已 abort 残留分片, objectName={}, uploadId={}", objectName, uploadId);
                } catch (Exception abortEx) {
                    log.warn("abort 残留分片失败, objectName={}, uploadId={}，将由桶生命周期规则兜底清理",
                            objectName, uploadId, abortEx);
                }
            }
            throw new BusinessException("分片上传失败", e);
        }
    }

    /**
     * 分片上传是典型的阻塞 IO 场景,适合直接用虚拟线程并发执行。
     * 优化: 预先读取文件到内存,避免重复IO
     */
    private List<PartETag> uploadPartsParallel(MultipartFile file,
                                               String objectName, String uploadId,
                                               int partCount, long fileSize) {
        // 将文件读取到内存,避免重复打开InputStream
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取文件失败", e);
        }
            
        List<CompletableFuture<PartETag>> futures = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) {
            int partNumber = i + 1;
            long startPos = i * PART_SIZE;
            long partSize = Math.min(PART_SIZE, fileSize - startPos);
    
            CompletableFuture<PartETag> future = CompletableFuture.supplyAsync(() -> {
                try (InputStream inputStream = new ByteArrayInputStream(
                        fileBytes, (int) startPos, (int) partSize)) {
                        
                    UploadPartRequest uploadPartRequest = new UploadPartRequest();
                    uploadPartRequest.setBucketName(ossProperties.getBucketName());
                    uploadPartRequest.setKey(objectName);
                    uploadPartRequest.setUploadId(uploadId);
                    uploadPartRequest.setInputStream(inputStream);
                    uploadPartRequest.setPartSize(partSize);
                    uploadPartRequest.setPartNumber(partNumber);
    
                    UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                    log.debug("分片 {}/{} 上传完成", partNumber, partCount);
                    return uploadPartResult.getPartETag();
                } catch (Exception e) {
                    throw new RuntimeException("分片 " + partNumber + " 上传失败", e);
                }
            }, virtualThreadExecutor);
    
            futures.add(future);
        }
    
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
        List<PartETag> partETags = new ArrayList<>(partCount);
        for (CompletableFuture<PartETag> future : futures) {
            partETags.add(future.join());
        }
        partETags.sort((a, b) -> a.getPartNumber() - b.getPartNumber());
        return partETags;
    }

    private String uploadSimple(MultipartFile file) {
        String objectName = buildObjectName(file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            if (StringUtils.hasText(file.getContentType())) {
                metadata.setContentType(file.getContentType());
            }
            // 添加文件MD5用于去重
            String md5 = DigestUtils.md5DigestAsHex(inputStream);
            metadata.setContentMD5(md5);
            // 重置流
            inputStream.reset();
            
            PutObjectRequest request = new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectName,
                    inputStream);
            request.setMetadata(metadata);
            ossClient.putObject(request);
            
            log.info("文件上传成功: {}", buildFileUrl(objectName));
        } catch (Exception e) {
            log.error("文件上传失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException("上传图片失败", e);
        }
        return buildFileUrl(objectName);
    }

    private OSS createOssClient() {
        // 配置客户端参数
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setMaxConnections(ossProperties.getMaxConnections());
        conf.setSocketTimeout(ossProperties.getSocketTimeout());
        conf.setConnectionTimeout(ossProperties.getConnectionTimeout());
        conf.setIdleConnectionTime(ossProperties.getIdleConnectionTimeout());
        conf.setMaxErrorRetry(ossProperties.getMaxErrorRetry());
        // 重试由 maxErrorRetry 控制，大于0时自动启用
        // 开启CRC校验
        conf.setCrcCheckEnabled(true);
        
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret(),
                conf);
    }

    private String buildObjectName(String originalFilename) {
        String folder = StringUtils.hasText(ossProperties.getFolder()) ? ossProperties.getFolder() : "images";
        String suffix = StringUtils.getFilenameExtension(originalFilename);
        suffix = StringUtils.hasText(suffix) ? suffix.toLowerCase() : "png";
        String dateFolder = LocalDate.now().toString();
        return folder + "/" + dateFolder + "/" + UUID.randomUUID() + "." + suffix;
    }

    private String buildFileUrl(String objectName) {
        String domain = ossProperties.getDomain();
        if (!StringUtils.hasText(domain)) {
            String endpoint = ossProperties.getEndpoint();
            if (!endpoint.startsWith("http")) {
                endpoint = "https://" + endpoint;
            }
            domain = endpoint;
            if (!domain.contains("://")) {
                domain = "https://" + domain;
            }
            String cleaned = domain.replaceFirst("^https?://", "");
            domain = "https://" + ossProperties.getBucketName() + "." + cleaned;
        }
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain + "/" + objectName;
    }
}
