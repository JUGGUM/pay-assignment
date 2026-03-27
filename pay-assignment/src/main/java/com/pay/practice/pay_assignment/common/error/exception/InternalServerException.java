package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class InternalServerException extends CustomBaseException {

    public InternalServerException(ErrorCode errorCode) {
        super(errorCode);
    }
}
