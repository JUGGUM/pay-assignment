package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class TooManyRequestsException extends CustomBaseException {

    public TooManyRequestsException(ErrorCode errorCode) {
        super(errorCode);
    }
}
