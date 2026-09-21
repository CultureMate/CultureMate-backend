package com.team.cultureevents.features.commons.handler;

/**
 * 공통 오류 응답 형식. docs/api-contract.md 참고.
 * 예: { "code": "UPSTREAM_UNAVAILABLE", "message": "잠시 후 다시 시도해 주세요." }
 */
public record ApiError(String code, String message) {
}
