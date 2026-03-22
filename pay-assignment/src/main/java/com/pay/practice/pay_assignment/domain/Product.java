package com.pay.practice.pay_assignment.domain;

import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.PaymentException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private Long price;

    public static Product create(String name, int stock, Long price) {
        Product product = new Product();
        product.name = name;
        product.stock = stock;
        product.price = price;
        return product;
    }

    /**
     * 재고 차감 - 도메인 내부에서 재고 부족 검증
     * 금융 시스템과 동일한 이유로 비즈니스 규칙을 엔티티 안에 둠:
     * 서비스 레이어가 바뀌어도 재고 음수 방지 로직은 항상 보장됨
     */
    public void decrease(int quantity) {
        if (this.stock < quantity) {
            throw new PaymentException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }

    public void increase(int quantity) {
        this.stock += quantity;
    }
}
