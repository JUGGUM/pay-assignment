package com.pay.practice.pay_assignment.config.error.exception;

import com.pay.practice.pay_assignment.config.error.ErrorCode;

public class BadRequestException extends CustomBaseException {

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
