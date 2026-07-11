package com.wensaas.core.web.ops;

import com.wensaas.core.api.menu.MenuAdminException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * {@link MenuAdminException} → {@link ProblemDetail}。
 *
 * <p>HTTP 映射：404 MENU_NOT_FOUND；409 NODE_KEY_CONFLICT；422 其余（绑码 / 形状 / 父节点 / 种子删除 / 有子节点）。
 */
@RestControllerAdvice
@Order(0)
public class MenuAdminRestExceptionAdvice {

    @ExceptionHandler(MenuAdminException.class)
    public ResponseEntity<ProblemDetail> onException(MenuAdminException e) {
        HttpStatus status = switch (e.code()) {
            case MENU_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NODE_KEY_CONFLICT -> HttpStatus.CONFLICT;
            case UNKNOWN_PERMISSION_CODE, INVALID_NODE_SHAPE, INVALID_PARENT,
                 CANNOT_DELETE_SEED, MENU_HAS_CHILDREN -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("Menu admin failed");
        pd.setType(URI.create("https://wen-saas-boot/errors/ops/menu/" + e.code().name().toLowerCase()));
        pd.setProperty("code", e.code().name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
