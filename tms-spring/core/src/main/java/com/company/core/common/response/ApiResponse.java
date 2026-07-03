package com.company.core.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 공통 API 응답 래퍼
 * 사용 예:
 *   return ResponseEntity.ok(ApiResponse.success(data));
 *   return ResponseEntity.ok(ApiResponse.created(data));
 *   return ResponseEntity.ok(ApiResponse.error("E001", "메시지"));
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String  code;
    private final String  message;
    private final T       data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code    = code;
        this.message = message;
        this.data    = data;
    }

    // ── 성공 응답 ──────────────────────────────────────────────
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "200", "OK", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "200", "OK", null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "201", "Created", data);
    }

    // ── 실패/에러 응답 ─────────────────────────────────────────
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    /** ok 필드 (Python Flask 호환: {"ok": true}) */
    public boolean isOk() { return success; }
}
