package com.ahead.aiagent.exception;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 业务异常：直接使用业务中抛出的 ErrorCode */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> businessExceptionHandler(BusinessException e) {
        log.error("businessException: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription()));
    }

    /** JWT 过期 → NOT_LOGIN(40100) */
    @ExceptionHandler(io.jsonwebtoken.ExpiredJwtException.class)
    public ResponseEntity<BaseResponse<?>> handleExpiredJwt(io.jsonwebtoken.ExpiredJwtException e) {
        log.warn("JWT 已过期: {}", e.getMessage());
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.NOT_LOGIN, "登录已过期，请重新登录"));
    }

    /** JWT 无效 → NOT_LOGIN(40100) */
    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public ResponseEntity<BaseResponse<?>> handleInvalidJwt(io.jsonwebtoken.JwtException e) {
        log.warn("JWT 无效: {}", e.getMessage());
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.NOT_LOGIN, "无效的认证信息"));
    }

    /** 参数校验失败 → PARAMS_ERROR(40000) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.PARAMS_ERROR, msg));
    }

    /**
     * 兜底：未知运行时异常 → SYSTEM_ERROR(50000)
     * 使用 ResponseEntity 避免 SSE 接口（produces=text/event-stream）抛异常时
     * 出现 HttpMediaTypeNotAcceptableException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseResponse<?>> runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException: {}", e.getMessage(), e);
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage(), ""));
    }
}
