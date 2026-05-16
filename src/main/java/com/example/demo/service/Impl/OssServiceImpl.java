package com.example.demo.service.Impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import com.example.demo.config.OssProperties;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.IOssService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class OssServiceImpl implements IOssService {

    @Resource
    private OssProperties ossProperties;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    /**
     * 分片大小：1MB
     */
    private static final long PART_SIZE = 1024 * 1024;

    /**
     * 大文件阈值：超过此大小使用分片上传（5MB）
     */
    private static final long LARGE_FILE_THRESHOLD = 5 * 1024 * 1024;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        // 大文件使用分片上传
        if (file.getSize() > LARGE_FILE_THRESHOLD) {
            return uploadLargeFile(file);
        }
        return uploadSimple(file);
    }

    @Override
    public String uploadLargeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String objectName = buildObjectName(file.getOriginalFilename());
        OSS ossClient = createOssClient();

        try {
            // 1. 初始化分片上传
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(
                    ossProperties.getBucketName(), objectName);
            if (StringUtils.hasText(file.getContentType())) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(file.getContentType());
                initRequest.setObjectMetadata(metadata);
            }
            InitiateMultipartUploadResult initResult = ossClient.initiateMultipartUpload(initRequest);
            String uploadId = initResult.getUploadId();

            // 2. 计算分片数量
            long fileSize = file.getSize();
            int partCount = (int) (fileSize / PART_SIZE);
            if (fileSize % PART_SIZE != 0) {
                partCount++;
            }

            // 3. 并行上传分片（使用虚拟线程）
            List<PartETag> partETags = uploadPartsParallel(ossClient, file, objectName, uploadId, partCount, fileSize);

            // 4. 完成分片上传
            CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(
                    ossProperties.getBucketName(), objectName, uploadId, partETags);
            ossClient.completeMultipartUpload(completeRequest);

            return buildFileUrl(objectName);
        } catch (Exception e) {
            throw new BusinessException("分片上传失败", e);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 并行上传分片（虚拟线程 + 自动上下文传播）
     */
    private List<PartETag> uploadPartsParallel(OSS ossClient, MultipartFile file,
                                                String objectName, String uploadId,
                                                int partCount, long fileSize) {
        List<PartETag> partETags = new ArrayList<>(partCount);

        List<CompletableFuture<PartETag>> futures = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) {
            int partNumber = i + 1;
            long startPos = i * PART_SIZE;
            long partSize = Math.min(PART_SIZE, fileSize - startPos);

            // 使用注入的 virtualThreadExecutor，自动传播上下文
            CompletableFuture<PartETag> future = CompletableFuture.supplyAsync(() -> {
                try (InputStream inputStream = file.getInputStream()) {
                    // 跳过前面的字节，定位到当前分片的起始位置
                    inputStream.skip(startPos);
                    
                    UploadPartRequest uploadPartRequest = new UploadPartRequest();
                    uploadPartRequest.setBucketName(ossProperties.getBucketName());
                    uploadPartRequest.setKey(objectName);
                    uploadPartRequest.setUploadId(uploadId);
                    uploadPartRequest.setInputStream(inputStream);
                    uploadPartRequest.setPartSize(partSize);
                    uploadPartRequest.setPartNumber(partNumber);

                    UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                    return uploadPartResult.getPartETag();
                } catch (Exception e) {
                    throw new RuntimeException("分片 " + partNumber + " 上传失败", e);
                }
            }, virtualThreadExecutor);

            futures.add(future);
        }

        // 等待所有分片上传完成，任一失败则全部失败
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<PartETag> future : futures) {
            partETags.add(future.join());
        }

        // 按分片号排序
        partETags.sort((a, b) -> a.getPartNumber() - b.getPartNumber());

        return partETags;
    }

    /**
     * 简单上传（小文件）
     */
    private String uploadSimple(MultipartFile file) {
        String objectName = buildObjectName(file.getOriginalFilename());
        OSS ossClient = createOssClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            if (StringUtils.hasText(file.getContentType())) {
                metadata.setContentType(file.getContentType());
            }
            PutObjectRequest request = new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectName,
                    inputStream);
            request.setMetadata(metadata);
            ossClient.putObject(request);
        } catch (Exception e) {
            throw new BusinessException("上传图片失败", e);
        } finally {
            ossClient.shutdown();
        }
        return buildFileUrl(objectName);
    }

    private OSS createOssClient() {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
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