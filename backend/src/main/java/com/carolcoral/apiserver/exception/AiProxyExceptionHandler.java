package com.carolcoral.apiserver.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局 AI 代理异常处理器
 * 处理内容协商等发生在 HandlerMethod 选择阶段的异常
 *
 * @author carolcoral
 */
@ControllerAdvice
public class AiProxyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiProxyExceptionHandler.class);

    /**
     * 处理 HttpMediaTypeNotAcceptableException
     * 当客户端 Accept header 不匹配时触发
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException e, HttpServletRequest request) {
        log.error("AI 接口内容协商失败: {}, clientIp={}, userAgent={}, Accept={}",
                e.getMessage(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Accept"));

        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> errDetail = new LinkedHashMap<>();
        errDetail.put("message", "AI 服务暂时不可用: 内容协商失败，请确保 Accept header 包含 application/json");
        errDetail.put("type", "api_error");
        errDetail.put("code", "406");
        error.put("error", errDetail);

        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }
}
