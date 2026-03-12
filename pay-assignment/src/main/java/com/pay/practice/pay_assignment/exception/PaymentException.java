package com.pay.practice.pay_assignment.exception;

import lombok.Getter;

@Getter
public class PaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PaymentException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
