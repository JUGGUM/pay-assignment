package com.pay.practice.pay_assignment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 멱등성 체크: 동일 idempotencyKey 로 이미 결제된 건이 있는지 조회
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
