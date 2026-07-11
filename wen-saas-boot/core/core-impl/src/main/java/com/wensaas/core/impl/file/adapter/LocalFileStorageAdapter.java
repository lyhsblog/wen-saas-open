package com.wensaas.core.impl.file.adapter;

import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.SignedUploadUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import com.wensaas.core.impl.file.CredentialCipher;

/**
 * 本地磁盘存储后端。开发态默认，正式环境一般用 OSS。
 *
 * <p>signedUploadUrl / signedDownloadUrl 返回带 HMAC token 的
 * {@code /api/v1/files/local-upload} / {@code /api/v1/files/local-download} URL，
 * 由 {@code LocalFileController} 校验签名后落盘 / 回流；
 * token 内含 objectKey + 过期戳 + HMAC-SHA256(key, objectKey + "|" + exp)。
 */
public class LocalFileStorageAdapter implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final Path root;
    /** 用于 signed URL token 签名；复用 CredentialCipher 的 32 字节密钥，避免再引一个配置项。 */
    private final byte[] hmacKey;

    public LocalFileStorageAdapter(Path root, byte[] hmacKey) {
        this.root = root;
        this.hmacKey = hmacKey;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "本地存储根目录创建失败: " + root, ex);
        }
        log.info("[FileStorage:Local] ready, root = {}", root.toAbsolutePath());
    }

    @Override
    public FileBackendType backendType() {
        return FileBackendType.LOCAL;
    }

    @Override
    public void upload(String objectKey, InputStream content, long contentLength, String mimeType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "本地存储写入失败: " + objectKey, ex);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "本地存储对象不存在: " + objectKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "本地存储读取失败: " + objectKey, ex);
        }
    }

    @Override
    public String signedDownloadUrl(String objectKey, Duration validity) {
        long exp = Instant.now().plus(validity).toEpochMilli();
        String token = sign(objectKey, exp);
        return "/api/v1/files/local-download?key=" + urlEncode(objectKey)
                + "&exp=" + exp + "&sig=" + token;
    }

    @Override
    public SignedUploadUrl signedUploadUrl(String objectKey, String mimeType, long contentLength,
                                           Duration validity) {
        long exp = Instant.now().plus(validity).toEpochMilli();
        String token = sign(objectKey, exp);
        String url = "/api/v1/files/local-upload?key=" + urlEncode(objectKey)
                + "&exp=" + exp + "&sig=" + token;
        return new SignedUploadUrl(
                /* fileId */ extractFileIdFromKey(objectKey),
                url,
                "PUT",
                mimeType == null ? Map.of() : Map.of("Content-Type", mimeType),
                Instant.ofEpochMilli(exp));
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "本地存储删除失败: " + objectKey, ex);
        }
    }

    @Override
    public BackendObjectInfo metadata(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            return null;
        }
        try {
            long size = Files.size(target);
            String mime = Files.probeContentType(target);
            return new BackendObjectInfo(size, mime);
        } catch (IOException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "本地存储元数据读取失败: " + objectKey, ex);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "objectKey 不能为空");
        }
        // 防 path traversal：拒绝 .. 与绝对路径
        if (objectKey.contains("..") || objectKey.startsWith("/") || objectKey.startsWith("\\")) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "非法 objectKey: " + objectKey);
        }
        return root.resolve(objectKey).normalize();
    }

    private String sign(String objectKey, long exp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGO));
            byte[] sig = mac.doFinal((objectKey + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR, "签名失败", ex);
        }
    }

    /** 暴露给上层 / 测试：校验签名是否合法且未过期。 */
    public boolean verifyToken(String objectKey, long exp, String providedSig) {
        if (Instant.now().toEpochMilli() > exp) {
            return false;
        }
        return sign(objectKey, exp).equals(providedSig);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 从 objectKey 中提取 fileId（路径最后一段）。 */
    private static String extractFileIdFromKey(String objectKey) {
        int idx = objectKey.lastIndexOf('/');
        return idx < 0 ? objectKey : objectKey.substring(idx + 1);
    }
}
