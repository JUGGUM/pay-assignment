package com.pay.practice.pay_assignment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class PaymentCompletedEvent extends ApplicationEvent {

    private final Long paymentId;
    private final Long userId;
    private final Long amount;
    private final String recipientEmail;  // 실제 서비스에서는 UserService 등에서 조회
    private final LocalDateTime completedAt;

    public PaymentCompletedEvent(Object source, Long paymentId, Long userId, Long amount, String recipientEmail) {
        super(source);
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.recipientEmail = recipientEmail;
        this.completedAt = LocalDateTime.now();
    }
}
