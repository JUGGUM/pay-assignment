package com.pay.practice.pay_assignment.domain;

import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.ConflictException;
import com.pay.practice.pay_assignment.common.error.exception.UnprocessableEntityException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment",
    indexes = {
        @Index(name = "idx_payment_idempotency_key", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_payment_order_id", columnList = "orderId")
    }
)
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    // 멱등성 키 - 동일 요청 중복 결제 방지
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    // 상품 연동 시 재고 복구를 위해 결제 시점의 productId/quantity 저장
    // null 허용: 상품 없이 단순 결제도 지원
    private Long productId;
    private Integer quantity;

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, CANCELLED;

        /**
         * 허용된 상태 전이 정의
         * - PENDING → SUCCESS, FAILED, CANCELLED
         * - SUCCESS → CANCELLED (정상 결제 취소/환불)
         * - FAILED, CANCELLED → 전이 불가 (단말 상태)
         * FAILED는 이미 트랜잭션 롤백으로 잔액이 복구됐으므로 취소 대상이 아님
         */
        public boolean canTransitionTo(PaymentStatus next) {
            return switch (this) {
                case PENDING  -> next == SUCCESS || next == FAILED || next == CANCELLED;
                case SUCCESS  -> next == CANCELLED;
                case FAILED, CANCELLED -> false;
            };
        }
    }

    public static Payment create(Long orderId, Long userId, Long amount, String idempotencyKey) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.amount = amount;
        payment.idempotencyKey = idempotencyKey;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    /** 결제에 상품 정보를 연결 - 취소 시 재고 복구에 사용 */
    public void attachProduct(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public void complete() {
        if (!status.canTransitionTo(PaymentStatus.SUCCESS)) {
            throw new UnprocessableEntityException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = PaymentStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        if (!status.canTransitionTo(PaymentStatus.FAILED)) {
            throw new UnprocessableEntityException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = PaymentStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        // 이미 취소된 건은 더 구체적인 에러 코드로 응답
        if (this.status == PaymentStatus.CANCELLED) {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_CANCELLED);
        }
        if (!status.canTransitionTo(PaymentStatus.CANCELLED)) {
            // SUCCESS가 아닌 상태(PENDING, FAILED)에서 취소 시도
            throw new UnprocessableEntityException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = PaymentStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }
}
