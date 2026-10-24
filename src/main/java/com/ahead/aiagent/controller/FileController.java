package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.FileArtifactService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时文件下载接口。
 */
@RestController
@RequestMapping("/files")
public class FileController {

    @Resource
    private FileArtifactService fileArtifactService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * 修复 O22：产物可回溯 —— 列出当前用户仍可下载的产物，供「我的文件」页面使用。
     * 解决「产物链接只通过一次 SSE 事件下发，客户端断连即丢失、事后找不回」的问题。
     */
    @GetMapping("/my-artifacts")
    public List<Map<String, Object>> listMyArtifacts() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "未登录");
        }
        return fileArtifactService.listByOwner(userId).stream()
                .map(meta -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("artifactId", meta.artifactId());
                    item.put("name", meta.fileName());
                    item.put("size", meta.size());
                    item.put("createdAt", meta.createdAt().toString());
                    item.put("expiresAt", meta.expireAt().toString());
                    item.put("downloadUrl", buildDownloadUrl(meta.artifactId(), meta.downloadToken()));
                    return item;
                })
                .toList();
    }

    private String buildDownloadUrl(String artifactId, String token) {
        String cp = (contextPath == null) ? "" : contextPath.trim();
        if (!cp.isEmpty() && !cp.startsWith("/")) {
            cp = "/" + cp;
        }
        if ("/".equals(cp)) {
            cp = "";
        }
        return cp + "/files/" + artifactId + "/download?token=" + token;
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> downloadFile(@PathVariable String fileId,
                                          @RequestParam(required = false) String token) {
        FileArtifactService.ArtifactMeta meta = fileArtifactService.getArtifact(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或已过期"));

        boolean tokenValid = fileArtifactService.validateDownloadToken(fileId, token);
        if (!tokenValid) {
            Long currentUserId = AuthContext.getUserId();
            if (meta.ownerUserId() != null && !meta.ownerUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载此文件");
            }
        }

        // 修复 O5：产物已外置到 OSS 时，直接重定向到预签名 URL（无需本地文件）
        String presignedUrl = fileArtifactService.resolvePresignedUrl(meta);
        if (presignedUrl != null) {
            return ResponseEntity.status(302).location(URI.create(presignedUrl)).build();
        }

        File file = new File(meta.absolutePath());
        if (!file.exists() || !file.isFile()) {
            fileArtifactService.removeArtifact(fileId, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或已删除");
        }

        String encodedFilename = URLEncoder.encode(meta.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        StreamingResponseBody stream = outputStream -> {
            byte[] buffer = new byte[8192];
            int len;
            try (InputStream input = new FileInputStream(file)) {
                while ((len = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }
            outputStream.flush();
        };

        MediaType mediaType = resolveMediaType(meta.fileName());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(meta.size())
                .body(stream);
    }

    private MediaType resolveMediaType(String fileName) {
        if (fileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".txt")) {
            return new MediaType("text", "plain", StandardCharsets.UTF_8);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
