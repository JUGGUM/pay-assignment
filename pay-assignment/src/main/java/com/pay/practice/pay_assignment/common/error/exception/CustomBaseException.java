package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public abstract class CustomBaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected CustomBaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
