package com.ahead.aiagent.utils;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.config.AliyunOssProperties;
import com.ahead.aiagent.exception.BusinessException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOSSOperator {

    private final AliyunOssProperties ossProperties;

    /** 允许上传的图片类型 */
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    /** 头像最大 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

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

        OSS ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
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
        } finally {
            ossClient.shutdown();
        }
    }
}
