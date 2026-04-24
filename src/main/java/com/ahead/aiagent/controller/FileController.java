package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.FileArtifactService;
import jakarta.annotation.Resource;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 临时文件下载接口。
 */
@RestController
@RequestMapping("/files")
public class FileController {

    @Resource
    private FileArtifactService fileArtifactService;

    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable String fileId,
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
