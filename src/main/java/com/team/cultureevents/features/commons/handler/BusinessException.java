package com.team.cultureevents.features.commons.handler;

import org.springframework.http.HttpStatus;

/** 비즈니스/업스트림 예외. GlobalExceptionHandler에서 HTTP 상태와 ApiError로 변환. */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException("INVALID_PARAM", message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("ALREADY_SAVED", message, HttpStatus.CONFLICT);
    }

    public static BusinessException upstream(String message) {
        return new BusinessException("UPSTREAM_UNAVAILABLE", message, HttpStatus.BAD_GATEWAY);
    }
}
