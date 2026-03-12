package com.pay.practice.pay_assignment;

import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.PaymentException;
import com.pay.practice.pay_assignment.service.ExternalPaymentClient;
import com.pay.practice.pay_assignment.service.PaymentService;
import com.pay.practice.pay_assignment.service.PaymentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * PaymentService 단위 테스트 (Mockito)
 *
 * 전략: DB/Redis 없이 순수하게 비즈니스 로직만 검증
 * - given/when/then (BDD 스타일)
 * - @Nested 로 시나리오별 그룹화 → 가독성 향상
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private ExternalPaymentClient externalPaymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PaymentValidator validator;

    @InjectMocks private PaymentService paymentService;

    private static final Long USER_ID   = 1L;
    private static final Long ORDER_ID  = 10L;
    private static final Long AMOUNT    = 5_000L;

    private Order pendingOrder;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.create(USER_ID, AMOUNT);
        wallet = Wallet.create(USER_ID, 100_000L);
    }

    // ────────────────────────────────────────────
    // 정상 결제
    // ────────────────────────────────────────────
    @Nested
    @DisplayName("결제 성공 시나리오")
    class SuccessCase {

        @Test
        @DisplayName("정상 요청 → SUCCESS 상태 Payment 반환, 이벤트 발행")
        void pay_success() {
            // given
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, AMOUNT, UUID.randomUUID().toString());

            given(validator.validateOrder(ORDER_ID)).willReturn(pendingOrder);
            given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet));
            given(externalPaymentClient.process(any())).willReturn(true);
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = paymentService.pay(req);

            // then
            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getAmount()).isEqualTo(AMOUNT);
            then(eventPublisher).should().publishEvent(any());  // 완료 이벤트 발행 확인
            then(validator).should().validateIdempotency(req.getIdempotencyKey());
        }
    }

    // ────────────────────────────────────────────
    // 검증 실패 시나리오
    // ────────────────────────────────────────────
    @Nested
    @DisplayName("검증 실패 시나리오")
    class ValidationFailCase {

        @Test
        @DisplayName("중복 idempotencyKey → DUPLICATE_IDEMPOTENCY_KEY 예외")
        void pay_duplicateIdempotencyKey() {
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, AMOUNT, "dup-key");

            // validator 가 예외를 던지도록 stubbing
            willThrow(new PaymentException(ErrorCode.DUPLICATE_IDEMPOTENCY_KEY))
                    .given(validator).validateIdempotency("dup-key");

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_IDEMPOTENCY_KEY);

            then(walletRepository).shouldHaveNoInteractions();  // 지갑까지 도달하지 않아야 함
        }

        @Test
        @DisplayName("존재하지 않는 주문 → ORDER_NOT_FOUND 예외")
        void pay_orderNotFound() {
            PaymentRequest req = PaymentRequest.of(999L, USER_ID, AMOUNT, UUID.randomUUID().toString());

            willThrow(new PaymentException(ErrorCode.ORDER_NOT_FOUND))
                    .given(validator).validateOrder(999L);

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 결제된 주문 → ORDER_ALREADY_PAID 예외")
        void pay_alreadyPaidOrder() {
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, AMOUNT, UUID.randomUUID().toString());

            willThrow(new PaymentException(ErrorCode.ORDER_ALREADY_PAID))
                    .given(validator).validateOrder(ORDER_ID);

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
        }

        @Test
        @DisplayName("지갑 없음 → WALLET_NOT_FOUND 예외")
        void pay_walletNotFound() {
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, AMOUNT, UUID.randomUUID().toString());

            given(validator.validateOrder(ORDER_ID)).willReturn(pendingOrder);
            given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        @DisplayName("잔액 부족 → INSUFFICIENT_BALANCE 예외, 이벤트 미발행")
        void pay_insufficientBalance() {
            Wallet poorWallet = Wallet.create(USER_ID, 1_000L);  // 잔액 1,000원
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, 5_000L, UUID.randomUUID().toString());

            given(validator.validateOrder(ORDER_ID)).willReturn(pendingOrder);
            given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(poorWallet));

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

            then(eventPublisher).shouldHaveNoInteractions();  // 실패 시 이벤트 없음
        }
    }

    // ────────────────────────────────────────────
    // 외부 결제 실패
    // ────────────────────────────────────────────
    @Nested
    @DisplayName("외부 결제 API 실패")
    class ExternalFailCase {

        @Test
        @DisplayName("외부 PG 실패 → PAYMENT_FAILED 예외")
        void pay_externalFail() {
            PaymentRequest req = PaymentRequest.of(ORDER_ID, USER_ID, AMOUNT, UUID.randomUUID().toString());

            given(validator.validateOrder(ORDER_ID)).willReturn(pendingOrder);
            given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet));
            given(externalPaymentClient.process(any())).willReturn(false);  // PG 실패

            assertThatThrownBy(() -> paymentService.pay(req))
                    .isInstanceOf(PaymentException.class)
                    .extracting(e -> ((PaymentException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }
}
