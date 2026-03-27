package com.pay.practice.pay_assignment.config.error.exception;

import com.pay.practice.pay_assignment.config.error.ErrorCode;

public class UnauthorizedException extends CustomBaseException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }
}
