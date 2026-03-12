package com.pay.practice.pay_assignment.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 비즈니스 예외 처리 (잔액 부족, 중복 결제 등)
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException e) {
        log.warn("[PaymentException] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    // @Valid 검증 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult br = e.getBindingResult();
        List<ErrorResponse.FieldError> errors = br.getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .reason(fe.getDefaultMessage())
                        .build())
                .toList();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
    }

    // 예상치 못한 예외 - 운영에서는 알림(Slack 등) 연동 권장
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[UnhandledException]", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
