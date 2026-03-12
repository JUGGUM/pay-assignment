package com.pay.practice.pay_assignment.domain;

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
        PENDING, PAID, FAILED, CANCELLED
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
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
    }

    public boolean isPaid() {
        return this.status == OrderStatus.PAID;
    }
}
