package com.pay.practice.pay_assignment.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 잔액/계정 오류
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "잔액이 부족합니다"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다"),

    // 주문 오류
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제 완료된 주문입니다"),

    // 결제 오류
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다"),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "동일한 요청 ID로 이미 결제가 처리되었습니다"),
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "외부 결제 처리 중 오류가 발생했습니다"),
    PAYMENT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 결제입니다"),
    PAYMENT_NOT_CANCELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "취소할 수 없는 결제 상태입니다"),

    // 상태 전이 오류
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "허용되지 않은 상태 전이입니다"),

    // 상품/재고 오류
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "재고가 부족합니다"),

    // 락 오류
    LOCK_ACQUISITION_FAILED(HttpStatus.TOO_MANY_REQUESTS, "현재 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요"),

    // 공통 오류
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
