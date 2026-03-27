package com.pay.practice.pay_assignment.common.error.exception;

import com.pay.practice.pay_assignment.common.error.ErrorCode;

public class UnprocessableEntityException extends CustomBaseException {

    public UnprocessableEntityException(ErrorCode errorCode) {
        super(errorCode);
    }
}
