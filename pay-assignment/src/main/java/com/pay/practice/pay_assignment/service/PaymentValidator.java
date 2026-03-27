package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.domain.Order;
import com.pay.practice.pay_assignment.domain.OrderRepository;
import com.pay.practice.pay_assignment.domain.Payment;
import com.pay.practice.pay_assignment.domain.PaymentRepository;
import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.ConflictException;
import com.pay.practice.pay_assignment.common.error.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 사전 검증 - Single Responsibility Principle
 *
 * PaymentService 에서 검증 로직을 분리한 이유:
 * - 검증 규칙이 늘어날 때 PaymentService 를 수정하지 않아도 됨
 * - PaymentValidator 단독으로 단위 테스트 가능
 * - 분산 락/비관적 락 서비스 양쪽에서 공유 사용 (중복 제거)
 */
@Component
@RequiredArgsConstructor
public class PaymentValidator {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 멱등성 검증 - 동일 키로 성공된 결제가 있으면 즉시 예외
     */
    public void validateIdempotency(String idempotencyKey) {
        paymentRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> {
                    if (existing.getStatus() == Payment.PaymentStatus.SUCCESS) {
                        throw new ConflictException(ErrorCode.DUPLICATE_IDEMPOTENCY_KEY);
                    }
                });
    }

    /**
     * 주문 검증 - 존재 여부 + 이미 결제 완료 여부
     */
    public Order validateOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        if (order.isPaid()) {
            throw new ConflictException(ErrorCode.ORDER_ALREADY_PAID);
        }
        return order;
    }
}
