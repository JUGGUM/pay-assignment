package com.pay.practice.pay_assignment.domain;

import com.pay.practice.pay_assignment.config.error.ErrorCode;
import com.pay.practice.pay_assignment.config.error.exception.BadRequestException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallet", indexes = @Index(name = "idx_wallet_user_id", columnList = "userId"))
@Getter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // userId 기준으로 비관적 락을 잡기 위해 별도 컬럼으로 분리
    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Long balance;

    // @Version: 낙관적 락 비교용 (비관적 락 사용 시에는 직접 충돌 감지에 사용하지 않지만, 변경 감지 보조 목적)
    @Version
    private Long version;

    public static Wallet create(Long userId, Long initialBalance) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.balance = initialBalance;
        return wallet;
    }

    // 잔액 차감 - 금융 정합성을 위해 도메인 내부에서 검증
    public void decrease(Long amount) {
        if (this.balance < amount) {
            throw new BadRequestException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }

    public void increase(Long amount) {
        this.balance += amount;
    }
}
