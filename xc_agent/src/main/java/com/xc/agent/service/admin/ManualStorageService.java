package com.xc.agent.service.admin;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AdminProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ManualStorageService {
    /**
     * 维修手册既要供用户下载，也要由 Python Agent 解析后写入向量库，因此上传格式必须
     * 与 RAG 解析器保持一致。旧版二进制 DOC 需要额外的系统转换工具，本项目不再接收。
     */
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private final Path root;
    private final long maxFileSize;

    public ManualStorageService(AdminProperties properties) {
        this.root = properties.manualStorageDirectory().toAbsolutePath().normalize();
        this.maxFileSize = properties.manualMaxFileSize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("MANUAL_FILE_REQUIRED", 400, "请选择要上传的手册文件");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("MANUAL_FILE_TOO_LARGE", 413, "手册文件不能超过 20 MB");
        }
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extension(original);
        if (!EXTENSIONS.contains(extension)) {
            throw new BusinessException("MANUAL_FILE_TYPE_UNSUPPORTED", 415,
                    "仅支持 PDF、DOCX、TXT、MD 文件");
        }
        String objectKey = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(root);
            byte[] bytes = file.getBytes();
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(objectKey, original,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    bytes.length, sha256(bytes));
        } catch (IOException exception) {
            throw new BusinessException("MANUAL_STORAGE_UNAVAILABLE", 503, "手册文件保存失败");
        }
    }

    public Path load(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("MANUAL_FILE_NOT_FOUND", 404, "手册文件不存在");
        }
        return path;
    }

    /**
     * 判断数据库中的对象键是否仍指向当前受管目录内的真实文件。
     *
     * <p>开发种子数据或历史版本可能保留旧式对象键。RAG 清单不能把这类记录交给
     * Agent，否则一条坏记录会让整次知识库同步失败。</p>
     */
    public boolean isAvailable(String objectKey) {
        try {
            return Files.isRegularFile(resolve(objectKey));
        } catch (BusinessException ignored) {
            return false;
        }
    }

    public void deleteQuietly(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ignored) {
            // Orphan cleanup may be retried manually; never mask the database result.
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || !objectKey.matches("^[a-f0-9]{32}\\.(pdf|docx|txt|md)$")) {
            throw new BusinessException("MANUAL_FILE_INVALID", 400, "手册文件标识无效");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("MANUAL_FILE_INVALID", 400, "手册文件路径无效");
        }
        return resolved;
    }

    private String safeOriginalName(String filename) {
        if (filename == null || filename.isBlank()) return "manual.txt";
        String safe = Path.of(filename.replace('\\', '/')).getFileName().toString();
        return safe.length() > 255 ? safe.substring(safe.length() - 255) : safe;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    public record StoredFile(String objectKey, String fileName, String contentType,
                             long fileSize, String sha256) {
    }
}
