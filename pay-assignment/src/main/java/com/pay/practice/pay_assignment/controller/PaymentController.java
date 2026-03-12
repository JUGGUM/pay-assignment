package com.pay.practice.pay_assignment.controller;

import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청
     * - Idempotency-Key 헤더 대신 body에 idempotencyKey 포함 (단순화)
     * - 실제 구현 시 @RequestHeader("Idempotency-Key") 방식도 고려
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.pay(request));
    }
}
