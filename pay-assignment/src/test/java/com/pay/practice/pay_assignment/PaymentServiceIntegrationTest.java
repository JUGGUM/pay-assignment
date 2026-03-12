package com.pay.practice.pay_assignment;

import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.exception.ErrorCode;
import com.pay.practice.pay_assignment.exception.PaymentException;
import com.pay.practice.pay_assignment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PaymentService 통합 테스트 (@SpringBootTest + H2)
 *
 * 단위 테스트와의 차이:
 * - 실제 JPA 트랜잭션, H2 DB, 비관적 락 동작을 검증
 * - 잔액 변화가 실제 DB에 반영되는지 확인
 * - @Transactional 없음: 각 테스트가 실제 커밋까지 가는지 확인 목적
 *   → @BeforeEach 에서 직접 deleteAll 로 정리
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;

    private static final Long USER_ID = 1L;
    private static final Long INITIAL_BALANCE = 50_000L;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
        walletRepository.save(Wallet.create(USER_ID, INITIAL_BALANCE));
    }

    @Test
    @DisplayName("결제 성공 → DB에 Payment 저장, 잔액 차감 확인")
    void pay_success_persistenceCheck() {
        Order order = orderRepository.save(Order.create(USER_ID, 10_000L));
        PaymentRequest req = PaymentRequest.of(order.getId(), USER_ID, 10_000L, UUID.randomUUID().toString());

        PaymentResponse response = paymentService.pay(req);

        // Payment 저장 확인
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        Payment saved = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        assertThat(saved.getIdempotencyKey()).isEqualTo(req.getIdempotencyKey());

        // 잔액 차감 확인
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE - 10_000L);
    }

    @Test
    @DisplayName("잔액 부족 → 예외 발생, 잔액 변동 없음 (트랜잭션 롤백 확인)")
    void pay_insufficientBalance_rollback() {
        Order order = orderRepository.save(Order.create(USER_ID, 100_000L));
        PaymentRequest req = PaymentRequest.of(order.getId(), USER_ID, 100_000L, UUID.randomUUID().toString());

        assertThatThrownBy(() -> paymentService.pay(req))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        // 잔액 변동 없음 검증
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("동일 idempotencyKey 재요청 → 두 번째는 DUPLICATE 예외")
    void pay_idempotency_duplicateRequest() {
        String key = UUID.randomUUID().toString();

        Order order1 = orderRepository.save(Order.create(USER_ID, 5_000L));
        paymentService.pay(PaymentRequest.of(order1.getId(), USER_ID, 5_000L, key));

        Order order2 = orderRepository.save(Order.create(USER_ID, 5_000L));
        assertThatThrownBy(() -> paymentService.pay(PaymentRequest.of(order2.getId(), USER_ID, 5_000L, key)))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_IDEMPOTENCY_KEY);

        // 첫 번째 결제 한 건만 저장됐는지 확인
        assertThat(paymentRepository.findByIdempotencyKey(key)).isPresent();
        assertThat(paymentRepository.count()).isEqualTo(1);

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE - 5_000L);
    }

    @Test
    @DisplayName("이미 결제된 주문 재결제 → ORDER_ALREADY_PAID 예외")
    void pay_alreadyPaidOrder() {
        Order order = orderRepository.save(Order.create(USER_ID, 5_000L));
        String key1 = UUID.randomUUID().toString();

        paymentService.pay(PaymentRequest.of(order.getId(), USER_ID, 5_000L, key1));

        assertThatThrownBy(() ->
                paymentService.pay(PaymentRequest.of(order.getId(), USER_ID, 5_000L, UUID.randomUUID().toString())))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
    }

    @Test
    @DisplayName("연속 결제 → 잔액이 누적 차감됨")
    void pay_sequential_balanceDecrement() {
        for (int i = 0; i < 5; i++) {
            Order order = orderRepository.save(Order.create(USER_ID, 5_000L));
            paymentService.pay(PaymentRequest.of(order.getId(), USER_ID, 5_000L, UUID.randomUUID().toString()));
        }

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE - 5_000L * 5);
    }
}
