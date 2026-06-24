package com.abzed.template.common;

import lombok.Getter;

/**
 * Standard response envelope used by every endpoint.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Request successful",
 *   "data": { "result": ... },
 *   "total": 0,
 *   "targetUrl": null,
 *   "status": 200
 * }
 * </pre>
 *
 * The business payload always lives under {@code data.result}; list endpoints
 * additionally set the top-level {@code total}. This matches the frontend
 * contract consumed by {@code usePaginatedQuery} ({@code data.total} +
 * {@code data.data.result}) and {@code useDynamicMutation} ({@code data.result}).
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final Body<T> data;
    private final long total;
    private final String targetUrl;
    private final int status;

    private ApiResponse(boolean success, String message, T result, long total, String targetUrl, int status) {
        this.success = success;
        this.message = message;
        this.data = success ? new Body<>(result) : null;
        this.total = total;
        this.targetUrl = targetUrl;
        this.status = status;
    }

    /** Wrapper so the payload is always exposed as {@code data.result}. */
    public record Body<T>(T result) {
    }

    public static <T> ApiResponse<T> success(String message, T result) {
        return new ApiResponse<>(true, message, result, 0, null, 200);
    }

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(true, "Success", result, 0, null, 200);
    }

    /** Success envelope for paginated/list payloads, carrying a top-level total. */
    public static <T> ApiResponse<T> page(String message, T result, long total) {
        return new ApiResponse<>(true, message, result, total, null, 200);
    }

    public static <T> ApiResponse<T> error(String message, int status) {
        return new ApiResponse<>(false, message, null, 0, null, status);
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(message, 400);
    }
}
