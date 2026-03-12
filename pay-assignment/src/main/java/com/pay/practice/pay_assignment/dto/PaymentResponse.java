package com.pay.practice.pay_assignment.dto;

import com.pay.practice.pay_assignment.domain.Payment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private String status;
    private String idempotencyKey;
    private LocalDateTime completedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .idempotencyKey(payment.getIdempotencyKey())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
