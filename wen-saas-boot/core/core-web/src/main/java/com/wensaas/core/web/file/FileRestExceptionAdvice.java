package com.wensaas.core.web.file;

import com.wensaas.core.api.file.FileException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link FileException} → {@link ProblemDetail}（与 {@code AuthRestExceptionAdvice} 同模式）。
 *
 * <p>Code → HTTP 映射：
 * <ul>
 *   <li>404: FILE_NOT_FOUND / SHARE_NOT_FOUND</li>
 *   <li>403: DOWNLOAD_FORBIDDEN / SHARE_PASSWORD_WRONG</li>
 *   <li>401: SHARE_PASSWORD_REQUIRED（提示客户端补密码后重试）</li>
 *   <li>410: SHARE_EXPIRED / SHARE_REVOKED</li>
 *   <li>429: SHARE_DOWNLOAD_LIMIT_EXCEEDED</li>
 *   <li>413: SIZE_EXCEEDED</li>
 *   <li>400: UPLOAD_NOT_CONFIRMABLE / INVALID_BACKEND_CONFIG</li>
 *   <li>500: 其余（NO_ACTIVE_BACKEND / 后端 IO / 加密 / 测试连接 / DUPLICATE_ACTIVE_CONFIG）</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class FileRestExceptionAdvice {

    @ExceptionHandler(FileException.class)
    public ResponseEntity<ProblemDetail> onFileException(FileException e) {
        HttpStatus status = switch (e.code()) {
            case FILE_NOT_FOUND, SHARE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DOWNLOAD_FORBIDDEN, SHARE_PASSWORD_WRONG -> HttpStatus.FORBIDDEN;
            case SHARE_PASSWORD_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case SHARE_EXPIRED, SHARE_REVOKED -> HttpStatus.GONE;
            case SHARE_DOWNLOAD_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UPLOAD_NOT_CONFIRMABLE, INVALID_BACKEND_CONFIG -> HttpStatus.BAD_REQUEST;
            // 测试连接失败＝调用方提供的 endpoint/bucket/凭证有误（可纠正的配置问题），用 422 而非 5xx，
            // 避免污染服务端故障监控；友好文案由 service 层归一（不透传底层 java.* 异常）。
            case TEST_CONNECTION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NO_ACTIVE_BACKEND, BACKEND_IO_ERROR,
                 CREDENTIAL_DECRYPT_ERROR, DUPLICATE_ACTIVE_CONFIG -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("File operation failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/file/"
                + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
