package com.pay.practice.pay_assignment.domain;

import com.pay.practice.pay_assignment.config.error.ErrorCode;
import com.pay.practice.pay_assignment.config.error.exception.BadRequestException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")  // order 는 SQL 예약어이므로 테이블명 변경
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum OrderStatus {
        PENDING, PAID, FAILED, CANCELLED;

        /**
         * 허용된 상태 전이 정의
         * - PENDING → PAID, FAILED, CANCELLED (결제 처리 중 다양한 결과)
         * - PAID → CANCELLED (환불/취소)
         * - FAILED, CANCELLED → 전이 불가 (단말 상태)
         * switch expression을 활용해 상태별 허용 목록을 명시적으로 관리
         */
        public boolean canTransitionTo(OrderStatus next) {
            return switch (this) {
                case PENDING  -> next == PAID || next == FAILED || next == CANCELLED;
                case PAID     -> next == CANCELLED;
                case FAILED, CANCELLED -> false;
            };
        }
    }

    public static Order create(Long userId, Long amount) {
        Order order = new Order();
        order.userId = userId;
        order.amount = amount;
        order.status = OrderStatus.PENDING;
        order.createdAt = LocalDateTime.now();
        return order;
    }

    public void markPaid() {
        if (!status.canTransitionTo(OrderStatus.PAID)) {
            throw new BadRequestException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        if (!status.canTransitionTo(OrderStatus.FAILED)) {
            throw new BadRequestException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = OrderStatus.FAILED;
    }

    public void cancel() {
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BadRequestException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isPaid() {
        return this.status == OrderStatus.PAID;
    }
}
