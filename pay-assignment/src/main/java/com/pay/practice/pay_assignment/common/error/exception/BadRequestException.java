package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class BadRequestException extends CustomBaseException {

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
