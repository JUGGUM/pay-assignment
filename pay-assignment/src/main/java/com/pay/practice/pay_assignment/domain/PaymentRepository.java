package com.pay.practice.pay_assignment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 멱등성 체크: 동일 idempotencyKey 로 이미 결제된 건이 있는지 조회
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * 비관적 락으로 결제 단건 조회 - 취소 요청 동시성 방지
     * 동일 paymentId로 동시 취소 요청이 들어올 경우
     * 첫 번째 요청이 락을 잡고 취소 처리 후 커밋되면
     * 두 번째 요청은 PAYMENT_ALREADY_CANCELLED 예외를 받게 됨
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(Long id);

    // 주문별 결제 목록 조회 - 재결제/부분결제 이력 확인용
    List<Payment> findAllByOrderId(Long orderId);
}
