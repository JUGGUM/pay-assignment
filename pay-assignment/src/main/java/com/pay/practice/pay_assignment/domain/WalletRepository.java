package com.pay.practice.pay_assignment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // 금융 정합성을 위해 DB 레벨에서 락을 거는 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(Long userId);
}