package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class ConflictException extends CustomBaseException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }
}
