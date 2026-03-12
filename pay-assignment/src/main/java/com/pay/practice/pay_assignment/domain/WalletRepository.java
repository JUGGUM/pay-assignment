package com.pay.practice.pay_assignment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    // 비관적 락: SELECT ... FOR UPDATE - DB 레벨에서 동시 접근 차단
    // 가상 스레드 환경에서도 DB 커넥션 풀 범위 내에서 안전하게 직렬화 보장
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(Long userId);
}
