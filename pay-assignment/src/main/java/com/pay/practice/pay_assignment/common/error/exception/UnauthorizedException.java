package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class UnauthorizedException extends CustomBaseException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }
}
