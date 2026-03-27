package com.pay.practice.pay_assignment.config.error;

import java.time.LocalDateTime;

public record ErrorResponse(
        String errorCode,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now()
        );
    }
}
