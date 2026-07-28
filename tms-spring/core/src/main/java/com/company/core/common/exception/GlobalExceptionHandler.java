package com.company.core.common.exception;

import com.company.core.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("유효성 검사 실패");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getCode(), msg));
    }

    /**
     * 정적 리소스를 찾지 못한 경우 (favicon.ico, 잘못된 URL 등) — 조용히 404 반환
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("[GlobalExceptionHandler] No static resource found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        // 기존에는 고정 메시지("서버 내부 오류가 발생했습니다.")만 반환하여
        // 실제 원인(Oracle 제약 위반, 바인딩 오류 등)이 응답/클라이언트에 전혀
        // 노출되지 않아 원격 디버깅이 불가능했다.
        // → 근본 원인(root cause)의 예외 타입 + 메시지를 함께 반환한다.
        Throwable root = rootCause(e);
        String detail = root.getClass().getSimpleName()
                + (root.getMessage() != null ? ": " + root.getMessage() : "");
        log.error("[GlobalExceptionHandler] Unhandled exception - {}", detail, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage() + " (" + detail + ")"));
    }

    /** 예외 체인을 따라 최초(근본) 원인을 찾는다. */
    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        int guard = 0;
        while (cur.getCause() != null && cur.getCause() != cur && guard++ < 20) {
            cur = cur.getCause();
        }
        return cur;
    }
}
