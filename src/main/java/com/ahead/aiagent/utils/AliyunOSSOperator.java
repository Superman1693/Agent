package com.ahead.aiagent.utils;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.config.AliyunOssProperties;
import com.ahead.aiagent.exception.BusinessException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOSSOperator {

    private final AliyunOssProperties ossProperties;

    /**
     * 修复 O10：OSS 客户端内部维护连接池，应作为单例复用，
     * 而不是每次 upload 都 new 一个再 shutdown。
     */
    private volatile OSS ossClient;

    /** 允许上传的图片类型 */
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    /** 头像最大 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(ossProperties.getAccessKeyId())) {
            log.warn("未配置 OSS AccessKey，头像上传能力将不可用（不影响应用启动）");
            return;
        }
        this.ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 上传文件到 OSS，返回可访问的 URL
     *
     * @param file MultipartFile
     * @param dir  OSS 目录前缀，如 "avatar/"
     * @return 文件访问 URL
     */
    public String upload(MultipartFile file, String dir) {
        // 校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (ossClient == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 未配置，暂时无法上传文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPG/PNG/GIF/WEBP 格式");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 5MB");
        }

        // 生成唯一文件名，保留原始扩展名
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String objectName = dir + UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            ossClient.putObject(ossProperties.getBucketName(), objectName,
                    new ByteArrayInputStream(file.getBytes()));
            String url = ossProperties.getDomain() + "/" + objectName;
            log.info("OSS 上传成功: {}", url);
            return url;
        } catch (OSSException | ClientException e) {
            log.error("OSS 上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        } catch (Exception e) {
            log.error("文件读取失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    /**
     * 修复 O5：OSS 是否已配置可用。
     */
    public boolean isConfigured() {
        return ossClient != null;
    }

    /**
     * 修复 O5：上传字节数组（用于把 Agent 产物外置到 OSS），返回 objectName。
     */
    public String uploadBytes(byte[] bytes, String objectName, String contentType) {
        if (ossClient == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 未配置，无法上传产物");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容为空");
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }
        ossClient.putObject(ossProperties.getBucketName(), objectName,
                new ByteArrayInputStream(bytes), metadata);
        return objectName;
    }

    /**
     * 修复 O5：生成带有效期的预签名下载 URL（bucket 为私有读时同样可访问）。
     */
    public String generatePresignedUrl(String objectName, long ttlSeconds) {
        if (ossClient == null || !StringUtils.hasText(objectName)) {
            return null;
        }
        long ttl = ttlSeconds <= 0 ? 3600L : ttlSeconds;
        Date expiration = new Date(System.currentTimeMillis() + ttl * 1000L);
        return ossClient.generatePresignedUrl(ossProperties.getBucketName(), objectName, expiration).toString();
    }

    /**
     * 修复 O5：删除 OSS 对象（产物过期清理时调用）。
     */
    public void deleteObject(String objectName) {
        if (ossClient == null || !StringUtils.hasText(objectName)) {
            return;
        }
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), objectName);
        } catch (Exception e) {
            log.warn("OSS 对象删除失败: {}", objectName, e);
        }
    }
}

