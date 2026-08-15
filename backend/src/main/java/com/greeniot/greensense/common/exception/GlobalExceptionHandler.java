package com.greeniot.greensense.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.greeniot.greensense.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns every escaped exception into the standard {@link ApiResponse} envelope. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
    }

    /**
     * JSON hỏng, sai kiểu, hoặc giá trị enum không tồn tại ({@code "command":"EXPLODE"}).
     *
     * <p>Không có handler này thì Jackson ném {@code HttpMessageNotReadableException} và
     * nó rơi thẳng xuống nhánh {@code Exception} — client nhận <b>500</b> và tưởng server
     * hỏng, trong khi lỗi hoàn toàn nằm ở request họ gửi.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();

        // Jackson đính kèm danh sách hằng số hợp lệ — thông tin đắt giá nhất cho người gọi,
        // nhưng nằm lẫn trong stack trace nên phải bóc ra tường minh.
        if (cause instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().isEmpty() ? "?"
                    : invalid.getPath().get(invalid.getPath().size() - 1).getFieldName();
            String allowed = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return build(HttpStatus.BAD_REQUEST, "INVALID_ENUM_VALUE",
                    "Giá trị '%s' không hợp lệ cho '%s'".formatted(invalid.getValue(), field),
                    Map.of("field", field, "allowed", allowed));
        }

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Không đọc được nội dung request", null);
    }

    /** Thiếu tham số bắt buộc trên query string. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Thiếu tham số bắt buộc: " + ex.getParameterName(),
                Map.of("parameter", ex.getParameterName()));
    }

    /** Tham số sai kiểu, ví dụ {@code ?range=} nhận enum nhưng truyền chuỗi lạ. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Giá trị không hợp lệ cho tham số '%s'".formatted(ex.getName()),
                Map.of("parameter", ex.getName()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials", null);
    }

    /** Last resort: log with a correlation id, hand the client the id and nothing else. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}]", correlationId, ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error. Reference: " + correlationId, null);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message, Object details) {
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(new ApiResponse.ApiError(code, message, details)));
    }
}
