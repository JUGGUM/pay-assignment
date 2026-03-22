package com.pay.practice.pay_assignment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 비관적 락으로 상품 조회 - SELECT ... FOR UPDATE
     * Wallet과 동일한 전략: 재고 차감도 금융 잔액 차감과 같은 수준의 정합성이 필요
     * 동시에 여러 요청이 같은 상품을 차감하려 할 때 DB 레벨에서 직렬화 보장
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(Long id);
}
