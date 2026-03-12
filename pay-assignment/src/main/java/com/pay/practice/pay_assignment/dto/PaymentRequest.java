package com.pay.practice.pay_assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequest {

    @NotNull(message = "orderId 는 필수입니다")
    private Long orderId;

    @NotNull(message = "userId 는 필수입니다")
    private Long userId;

    @NotNull(message = "amount 는 필수입니다")
    @Positive(message = "amount 는 양수여야 합니다")
    private Long amount;

    // 멱등성 키: 클라이언트가 생성하는 UUID - 네트워크 오류로 인한 중복 요청 방지
    @NotBlank(message = "idempotencyKey 는 필수입니다")
    private String idempotencyKey;

    // 테스트/내부 생성용 팩토리 - Jackson 역직렬화는 @NoArgsConstructor 사용
    public static PaymentRequest of(Long orderId, Long userId, Long amount, String idempotencyKey) {
        PaymentRequest r = new PaymentRequest();
        r.orderId = orderId;
        r.userId = userId;
        r.amount = amount;
        r.idempotencyKey = idempotencyKey;
        return r;
    }
}
