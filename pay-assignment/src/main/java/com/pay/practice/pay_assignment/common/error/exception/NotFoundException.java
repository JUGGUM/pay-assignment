package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class NotFoundException extends CustomBaseException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
