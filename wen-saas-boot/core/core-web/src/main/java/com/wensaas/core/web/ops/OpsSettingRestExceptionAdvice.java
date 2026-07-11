package com.wensaas.core.web.ops;

import com.wensaas.core.api.ops.setting.OpsSettingException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link OpsSettingException} → {@link ProblemDetail}。
 */
@RestControllerAdvice
@Order(0)
public class OpsSettingRestExceptionAdvice {

    @ExceptionHandler(OpsSettingException.class)
    public ResponseEntity<ProblemDetail> onException(OpsSettingException e) {
        HttpStatus status = switch (e.code()) {
            case SETTING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_VALUE, UNKNOWN_CATEGORY -> HttpStatus.BAD_REQUEST;
            case STORAGE_TEST_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Ops setting failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/setting/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
