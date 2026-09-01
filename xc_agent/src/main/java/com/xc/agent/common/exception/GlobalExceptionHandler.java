package com.xc.agent.common.exception;

import com.xc.agent.common.api.FieldErrorVO;
import com.xc.agent.common.api.ProblemDetailVO;
import com.xc.agent.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetailVO> handleBusiness(BusinessException exception,
                                                           HttpServletRequest request) {
        log.warn("Business request rejected, status={}, code={}, requestId={}",
                exception.getStatus(), exception.getCode(), RequestIdFilter.get(request));
        return problem(exception.getStatus(), exception.getCode(), title(exception.getStatus()),
                exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailVO> handleValidation(MethodArgumentNotValidException exception,
                                                             HttpServletRequest request) {
        List<FieldErrorVO> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        log.warn("Validation failed, fields={}, requestId={}",
                errors.stream().map(FieldErrorVO::field).distinct().toList(), RequestIdFilter.get(request));
        return problem(400, "VALIDATION_FAILED", "请求参数错误", "请检查请求字段", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailVO> handleUnreadable(HttpMessageNotReadableException exception,
                                                             HttpServletRequest request) {
        log.warn("Unreadable request body, requestId={}", RequestIdFilter.get(request));
        return problem(400, "VALIDATION_FAILED", "请求参数错误",
                "请求 JSON 格式错误或登录模式不受支持", List.of(), request);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ProblemDetailVO> handleRedis(RedisConnectionFailureException exception,
                                                        HttpServletRequest request) {
        log.error("Redis connection failed, requestId={}", RequestIdFilter.get(request), exception);
        return problem(503, "DEPENDENCY_UNAVAILABLE", "依赖服务不可用",
                "验证码服务暂时不可用", List.of(), request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetailVO> handleRequestBinding(Exception exception,
                                                                 HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", "请求参数错误",
                "请求头或查询参数格式不正确", List.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetailVO> handleUploadSize(MaxUploadSizeExceededException exception,
                                                             HttpServletRequest request) {
        return problem(413, "MANUAL_FILE_TOO_LARGE", "上传文件过大",
                "手册文件不能超过 20 MB", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetailVO> handleConflict(DataIntegrityViolationException exception,
                                                           HttpServletRequest request) {
        log.warn("Database constraint conflict, errorType={}, requestId={}",
                exception.getClass().getSimpleName(), RequestIdFilter.get(request));
        return problem(409, "RESOURCE_CONFLICT", "请求冲突",
                "资源名称重复或仍被其他数据引用", List.of(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetailVO> handleDatabase(DataAccessException exception,
                                                           HttpServletRequest request) {
        log.error("Database access failed, errorType={}, requestId={}",
                exception.getClass().getSimpleName(), RequestIdFilter.get(request), exception);
        return problem(503, "DEPENDENCY_UNAVAILABLE", "依赖服务不可用",
                "数据库服务暂时不可用", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailVO> handleUnexpected(Exception exception,
                                                             HttpServletRequest request) {
        log.error("Unhandled error, requestId={}", RequestIdFilter.get(request), exception);
        return problem(500, "INTERNAL_ERROR", "服务器内部错误",
                "服务器处理请求时发生错误", List.of(), request);
    }

    private FieldErrorVO toFieldError(FieldError error) {
        return new FieldErrorVO(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ProblemDetailVO> problem(int status, String code, String title,
                                                     String detail, List<FieldErrorVO> errors,
                                                     HttpServletRequest request) {
        ProblemDetailVO body = new ProblemDetailVO(
                "about:blank", title, status, code, detail, RequestIdFilter.get(request), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private String title(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            return "请求处理失败";
        }
        return switch (httpStatus) {
            case BAD_REQUEST -> "请求参数错误";
            case UNAUTHORIZED -> "认证失败";
            case FORBIDDEN -> "禁止访问";
            case NOT_FOUND -> "资源不存在";
            case CONFLICT -> "请求冲突";
            case SERVICE_UNAVAILABLE -> "依赖服务不可用";
            default -> "请求处理失败";
        };
    }
}
