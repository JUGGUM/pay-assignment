package com.pay.practice.pay_assignment.config.error.exception;

import com.pay.practice.pay_assignment.config.error.ErrorCode;

public class NotFoundException extends CustomBaseException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
