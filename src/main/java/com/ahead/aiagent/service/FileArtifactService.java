package com.ahead.aiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时文件索引服务：维护 artifactId 与本地文件映射。
 */
@Service
@Slf4j
public class FileArtifactService {

    private static final long DEFAULT_TTL_MINUTES = 120;

    private final Map<String, ArtifactMeta> artifactMap = new ConcurrentHashMap<>();

    public String registerPdfArtifact(String absolutePath, String fileName, Long ownerUserId) {
        String artifactId = "a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String downloadToken = "dl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        File file = new File(absolutePath);
        OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(DEFAULT_TTL_MINUTES);
        ArtifactMeta meta = new ArtifactMeta(
                artifactId,
                absolutePath,
                fileName,
                ownerUserId,
                OffsetDateTime.now(),
                expireAt,
            file.length(),
            downloadToken);
        artifactMap.put(artifactId, meta);
        return artifactId;
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

    public void bindOwner(String artifactId, Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        artifactMap.computeIfPresent(artifactId, (id, old) -> new ArtifactMeta(
                old.artifactId(),
                old.absolutePath(),
                old.fileName(),
                ownerUserId,
                old.createdAt(),
                old.expireAt(),
                old.size(),
                old.downloadToken()));
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
        if (removed == null || !deleteFile) {
            return;
        }
        File file = new File(removed.absolutePath());
        if (file.exists() && !file.delete()) {
            log.warn("临时文件删除失败: {}", removed.absolutePath());
        }
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
