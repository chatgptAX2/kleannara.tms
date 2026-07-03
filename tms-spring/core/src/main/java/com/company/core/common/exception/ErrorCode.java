package com.company.core.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공통 에러 코드
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────
    INVALID_INPUT        ("E400", "잘못된 입력값입니다."),
    ENTITY_NOT_FOUND     ("E404", "리소스를 찾을 수 없습니다."),
    UNAUTHORIZED         ("E401", "인증이 필요합니다."),
    FORBIDDEN            ("E403", "권한이 없습니다."),
    INTERNAL_ERROR       ("E500", "서버 내부 오류가 발생했습니다."),

    // ── 배차(dispatch) ────────────────────────────
    DISPATCH_NOT_FOUND   ("D404", "배차 정보를 찾을 수 없습니다."),
    DISPATCH_ALREADY_CONFIRMED("D409", "이미 확정된 배차입니다."),
    DISPATCH_AUTO_FAILED ("D500", "자동배차 처리 중 오류가 발생했습니다."),

    // ── 차량(vehicle) ─────────────────────────────
    VEHICLE_NOT_FOUND    ("V404", "차량 정보를 찾을 수 없습니다."),
    CARTYPE_DUPLICATE    ("V409", "이미 등록된 차종입니다."),

    // ── 납품문서(delivery) ────────────────────────
    DELIVERY_NOT_FOUND   ("L404", "납품문서를 찾을 수 없습니다."),

    // ── 배차제약(dispatch-config) ─────────────────
    OBJECTIVE_NOT_FOUND  ("C404", "목적식을 찾을 수 없습니다."),
    PROFILE_NOT_FOUND    ("C405", "프로파일을 찾을 수 없습니다.");

    private final String code;
    private final String message;
}
