package org.xhy.raglearn.common.api;

/**
 * Unified response wrapper for the learning backend.
 *
 * @param success whether the request finished successfully
 * @param code business-friendly response code
 * @param message readable response message
 * @param data payload
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "Request succeeded.", data);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}

