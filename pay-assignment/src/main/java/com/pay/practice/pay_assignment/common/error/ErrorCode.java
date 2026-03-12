package com.pay.practice.pay_assignment.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 잔액/계정 오류
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "PAY-001", "잔액이 부족합니다"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-002", "지갑을 찾을 수 없습니다"),

    // 주문 오류
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORD-001", "주문을 찾을 수 없습니다"),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "ORD-002", "이미 결제 완료된 주문입니다"),

    // 결제 오류
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PMT-001", "결제 내역을 찾을 수 없습니다"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PMT-002", "이미 처리된 결제입니다"),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "PMT-003", "동일한 요청 ID로 이미 결제가 처리되었습니다"),
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PMT-004", "외부 결제 처리 중 오류가 발생했습니다"),
    PAYMENT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "PMT-005", "이미 취소된 결제입니다"),
    PAYMENT_NOT_CANCELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "PMT-006", "취소할 수 없는 결제 상태입니다"),

    // 락 오류
    LOCK_ACQUISITION_FAILED(HttpStatus.TOO_MANY_REQUESTS, "LCK-001", "현재 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요"),

    // 공통 오류
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "CMN-001", "잘못된 입력값입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CMN-999", "서버 내부 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
