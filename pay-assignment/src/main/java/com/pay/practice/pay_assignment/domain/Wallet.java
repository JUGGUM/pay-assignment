package com.pay.practice.pay_assignment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Wallet {

    @Id
    private Long userId;
    private Long balance;

    public void decrease(Long amount) {
        if (this.balance < amount) {
            throw new IllegalArgumentException("잔액 부족");
        }
        this.balance -= amount;
    }
}
