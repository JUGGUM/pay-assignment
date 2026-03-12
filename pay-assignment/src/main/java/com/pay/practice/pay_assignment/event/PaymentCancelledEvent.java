package com.pay.practice.pay_assignment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class PaymentCancelledEvent extends ApplicationEvent {

    private final Long paymentId;
    private final Long userId;
    private final Long refundAmount;
    private final LocalDateTime cancelledAt;

    public PaymentCancelledEvent(Object source, Long paymentId, Long userId, Long refundAmount) {
        super(source);
        this.paymentId = paymentId;
        this.userId = userId;
        this.refundAmount = refundAmount;
        this.cancelledAt = LocalDateTime.now();
    }
}
