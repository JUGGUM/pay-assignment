package com.pay.practice.pay_assignment.controller;

import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 결제 취소
     * - DELETE 시맨틱: 결제 리소스를 '취소' 상태로 전이 (실제 삭제 아님)
     * - 비관적 락으로 동시 취소 요청 방지
     */
    @DeleteMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.cancel(paymentId));
    }

    /**
     * 결제 단건 조회
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    /**
     * 주문별 결제 목록 조회
     * - 재결제 이력이 있는 경우 여러 건 반환
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrderId(@RequestParam Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrderId(orderId));
    }
}
