package com.wensaas.core.web.file;

import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.storage.FileStorageFactory;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 本地存储后端的真实上传 / 下载端点。配合 {@link LocalFileStorageAdapter#signedUploadUrl}
 * / {@link LocalFileStorageAdapter#signedDownloadUrl} 生成的 HMAC token URL 使用。
 *
 * <p>两个端点标 {@link PublicEndpoint}：HMAC token 自带鉴权（密钥 = active AES key 的 32 字节）；
 * 不依赖 UserContext / TenantContext。objectKey 必须以 {@code tenants/} 开头防越权。
 *
 * <p>HTTP 错误码：
 * <ul>
 *   <li>400 BAD_REQUEST: objectKey 不合法</li>
 *   <li>401 UNAUTHORIZED: HMAC 签名不匹配</li>
 *   <li>404 NOT_FOUND: 对象不存在</li>
 *   <li>410 GONE: 签名 URL 已过期（exp &lt; now）</li>
 * </ul>
 */
@Tag(name = "files-local", description = "本地存储后端真实上传/下载（HMAC 签名 URL 自鉴权，无需 JWT）")
@SecurityRequirements // HMAC URL 自鉴权，覆盖全局 bearer-jwt
@RestController
@RequestMapping("/api/v1/files")
public class LocalFileController {

    private static final Logger log = LoggerFactory.getLogger(LocalFileController.class);
    /** objectKey 必须以 tenants/ 开头；只允许字母数字 / _ - . / 字符。 */
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile("^tenants/[A-Za-z0-9_./\\-]+$");

    private final FileStorageFactory factory;

    public LocalFileController(FileStorageFactory factory) {
        this.factory = factory;
    }

    @PublicEndpoint
    @Operation(summary = "本地存储下载", description = "HMAC 签名 URL 自鉴权；URL 由 LocalFileStorageAdapter.signedDownloadUrl 生成")
    @GetMapping("/local-download")
    public void download(
            @RequestParam("key") String objectKey,
            @RequestParam("exp") long exp,
            @RequestParam("sig") String sig,
            HttpServletResponse response) throws IOException {

        verifyHmacOrThrow(objectKey, exp, sig);
        LocalFileStorageAdapter adapter = factory.localAdapter();
        FileStorage.BackendObjectInfo meta = adapter.metadata(objectKey);
        if (meta == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND, "对象不存在: " + objectKey);
        }
        MediaType type = meta.mimeType() != null
                ? MediaType.parseMediaType(meta.mimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        response.setContentType(type.toString());
        response.setContentLengthLong(meta.sizeBytes());
        try (InputStream in = adapter.download(objectKey)) {
            in.transferTo(response.getOutputStream());
        }
        response.flushBuffer();
    }

    @PublicEndpoint
    @Operation(summary = "本地存储上传", description = "HMAC 签名 URL 自鉴权；URL 由 LocalFileStorageAdapter.signedUploadUrl 生成")
    @PutMapping("/local-upload")
    public ResponseEntity<Void> upload(
            @RequestParam("key") String objectKey,
            @RequestParam("exp") long exp,
            @RequestParam("sig") String sig,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String mimeType,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false) Long contentLength,
            HttpServletRequest request) throws IOException {

        verifyHmacOrThrow(objectKey, exp, sig);
        LocalFileStorageAdapter adapter = factory.localAdapter();
        try (InputStream body = request.getInputStream()) {
            adapter.upload(objectKey, body,
                    contentLength == null ? -1L : contentLength,
                    mimeType);
        }
        log.debug("[LocalFile] uploaded objectKey={}", objectKey);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * 三段校验：(1) objectKey 合法 → 400；(2) exp 未过期 → 410；(3) HMAC 签名匹配 → 401。
     */
    private void verifyHmacOrThrow(String objectKey, long exp, String sig) {
        if (objectKey == null || !OBJECT_KEY_PATTERN.matcher(objectKey).matches()
                || objectKey.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "objectKey 不合法（必须 tenants/...）");
        }
        if (Instant.now().toEpochMilli() > exp) {
            throw new ResponseStatusException(HttpStatus.GONE, "签名 URL 已过期");
        }
        if (!factory.localAdapter().verifyToken(objectKey, exp, sig)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "签名校验失败");
        }
    }
}
