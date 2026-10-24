package com.ahead.aiagent.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.constant.FileConstant;
import com.ahead.aiagent.utils.AliyunOSSOperator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时文件索引服务：维护 artifactId 与本地文件映射。
 *
 * 修复 E16/D18：索引持久化到磁盘，避免服务重启后索引清空、用户手上已有下载链接失效
 * 且磁盘文件变成永不清理的孤儿文件。
 * 说明：这是单机持久化方案；多实例部署仍建议将产物元数据外置（Redis / OSS 预签名 URL）。
 */
@Service
@Slf4j
public class FileArtifactService {

    private static final long DEFAULT_TTL_MINUTES = 120;

    private final Map<String, ArtifactMeta> artifactMap = new ConcurrentHashMap<>();

    /** 索引文件路径 */
    private final File indexFile = new File(FileConstant.FILE_SAVE_DIR + "/artifacts/index.json");

    /** 修复 O5：产物可外置到 OSS（可选，默认关闭；未启用时保持本地存储行为不变） */
    @Resource
    private AliyunOSSOperator ossOperator;

    @Value("${app.artifact.oss-enabled:false}")
    private boolean ossEnabled;

    /** OSS 存储标记前缀（存于 ArtifactMeta.absolutePath） */
    private static final String OSS_PATH_PREFIX = "oss://";

    @PostConstruct
    public void loadIndex() {
        try {
            if (!indexFile.exists()) {
                return;
            }
            String content = FileUtil.readUtf8String(indexFile);
            JSONArray arr = JSONUtil.parseArray(content);
            OffsetDateTime now = OffsetDateTime.now();
            int restored = 0;
            int skipped = 0;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                try {
                    OffsetDateTime expireAt = OffsetDateTime.parse(o.getStr("expireAt"));
                    if (expireAt.isBefore(now)) {
                        skipped++;
                        continue;
                    }
                    ArtifactMeta meta = new ArtifactMeta(
                            o.getStr("artifactId"),
                            o.getStr("absolutePath"),
                            o.getStr("fileName"),
                            o.getLong("ownerUserId"),
                            OffsetDateTime.parse(o.getStr("createdAt")),
                            expireAt,
                            o.getLong("size", 0L),
                            o.getStr("downloadToken"));
                    artifactMap.put(meta.artifactId(), meta);
                    restored++;
                } catch (Exception itemError) {
                    log.warn("跳过无法解析的产物索引项: {}", o, itemError);
                }
            }
            log.info("产物索引恢复完成：恢复 {} 条，跳过已过期 {} 条", restored, skipped);
        } catch (Exception e) {
            log.error("恢复产物索引失败", e);
        }
    }

    private synchronized void persistIndex() {
        try {
            JSONArray arr = new JSONArray();
            for (ArtifactMeta m : artifactMap.values()) {
                JSONObject o = new JSONObject();
                o.set("artifactId", m.artifactId());
                o.set("absolutePath", m.absolutePath());
                o.set("fileName", m.fileName());
                o.set("ownerUserId", m.ownerUserId());
                o.set("createdAt", m.createdAt().toString());
                o.set("expireAt", m.expireAt().toString());
                o.set("size", m.size());
                o.set("downloadToken", m.downloadToken());
                arr.add(o);
            }
            FileUtil.mkdir(indexFile.getParentFile());
            FileUtil.writeUtf8String(arr.toStringPretty(), indexFile);
        } catch (Exception e) {
            log.error("持久化产物索引失败", e);
        }
    }

    public String registerPdfArtifact(String absolutePath, String fileName, Long ownerUserId) {
        return registerArtifact(absolutePath, fileName, ownerUserId);
    }

    public String registerArtifact(String absolutePath, String fileName, Long ownerUserId) {
        String artifactId = "a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String downloadToken = "dl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        File file = new File(absolutePath);
        long size = file.length();

        // 修复 O5：启用 OSS 时把产物外置，天然无状态、多实例共享，并由 OSS 负责生命周期
        String storagePath = absolutePath;
        if (ossEnabled && ossOperator != null && ossOperator.isConfigured()) {
            String objectName = "artifacts/" + artifactId + "_" + fileName;
            try {
                ossOperator.uploadBytes(FileUtil.readBytes(file), objectName, resolveContentType(fileName));
                storagePath = OSS_PATH_PREFIX + objectName;
                log.info("产物已外置到 OSS: {}", objectName);
            } catch (Exception e) {
                log.warn("产物上传 OSS 失败，回退本地存储: {}", e.getMessage());
            }
        }

        OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(DEFAULT_TTL_MINUTES);
        ArtifactMeta meta = new ArtifactMeta(
                artifactId,
                storagePath,
                fileName,
                ownerUserId,
                OffsetDateTime.now(),
                expireAt,
            size,
            downloadToken);
        artifactMap.put(artifactId, meta);
        persistIndex();
        return artifactId;
    }

    /** 修复 O5：生成 OSS 预签名下载 URL；非 OSS 产物返回 null（由调用方回退本地流式下载） */
    public String resolvePresignedUrl(ArtifactMeta meta) {
        if (meta == null || meta.absolutePath() == null || !meta.absolutePath().startsWith(OSS_PATH_PREFIX)) {
            return null;
        }
        if (ossOperator == null) {
            return null;
        }
        String objectName = meta.absolutePath().substring(OSS_PATH_PREFIX.length());
        return ossOperator.generatePresignedUrl(objectName, DEFAULT_TTL_MINUTES * 60);
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        }
        return "application/octet-stream";
    }

    public Optional<ArtifactMeta> getArtifact(String artifactId) {
        ArtifactMeta meta = artifactMap.get(artifactId);
        if (meta == null) {
            return Optional.empty();
        }
        if (meta.expireAt().isBefore(OffsetDateTime.now())) {
            removeArtifact(artifactId, false);
            return Optional.empty();
        }
        return Optional.of(meta);
    }

    /** 修复 O22：列出某用户当前有效的产物，用于「我的文件」回溯 */
    public List<ArtifactMeta> listByOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();
        return artifactMap.values().stream()
                .filter(meta -> ownerUserId.equals(meta.ownerUserId()))
                .filter(meta -> !meta.expireAt().isBefore(now))
                .sorted(Comparator.comparing(ArtifactMeta::createdAt).reversed())
                .toList();
    }

    public void bindOwner(String artifactId, Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        boolean changed = artifactMap.computeIfPresent(artifactId, (id, old) -> new ArtifactMeta(
                old.artifactId(),
                old.absolutePath(),
                old.fileName(),
                ownerUserId,
                old.createdAt(),
                old.expireAt(),
                old.size(),
                old.downloadToken())) != null;
        if (changed) {
            persistIndex();
        }
    }

    public boolean validateDownloadToken(String artifactId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return getArtifact(artifactId)
                .map(meta -> token.equals(meta.downloadToken()))
                .orElse(false);
    }

    public void removeArtifact(String artifactId, boolean deleteFile) {
        ArtifactMeta removed = artifactMap.remove(artifactId);
        if (removed == null) {
            return;
        }
        if (deleteFile) {
            if (removed.absolutePath() != null && removed.absolutePath().startsWith(OSS_PATH_PREFIX)) {
                // 修复 O5：外置产物需同时删除对应的 OSS 对象
                if (ossOperator != null) {
                    ossOperator.deleteObject(removed.absolutePath().substring(OSS_PATH_PREFIX.length()));
                }
            } else {
                File file = new File(removed.absolutePath());
                if (file.exists() && !file.delete()) {
                    log.warn("临时文件删除失败: {}", removed.absolutePath());
                }
            }
        }
        persistIndex();
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanupExpiredArtifacts() {
        OffsetDateTime now = OffsetDateTime.now();
        artifactMap.values().stream()
                .filter(meta -> meta.expireAt().isBefore(now))
                .map(ArtifactMeta::artifactId)
                .toList()
                .forEach(id -> removeArtifact(id, true));
    }

    public record ArtifactMeta(
            String artifactId,
            String absolutePath,
            String fileName,
            Long ownerUserId,
            OffsetDateTime createdAt,
            OffsetDateTime expireAt,
            long size,
            String downloadToken
    ) {
    }
}
