package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.event.PaymentCompletedEvent;
import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비관적 락(PESSIMISTIC_WRITE) 기반 결제 서비스
 *
 * 선택 이유:
 * - 금융 시스템은 충돌 가능성이 높고, 충돌 시 재처리 비용이 큼
 * - 낙관적 락은 재시도 로직 필요 + 사용자 경험 저하
 * - 비관적 락은 DB 트랜잭션 범위 내에서 순서를 보장하므로 잔액 정합성에 적합
 *
 * 가상 스레드와의 조합:
 * - 가상 스레드는 I/O 대기 중 캐리어 스레드를 블로킹하지 않음
 * - DB 락 대기 중에도 OS 스레드 낭비 없이 수천 개의 동시 요청 처리 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final ExternalPaymentClient externalPaymentClient;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentValidator validator;  // 검증 로직 위임

    @Transactional
    public PaymentResponse pay(PaymentRequest request) {

        // 1. 멱등성 + 주문 상태 검증 (PaymentValidator 에 위임)
        validator.validateIdempotency(request.getIdempotencyKey());
        Order order = validator.validateOrder(request.getOrderId());

        // 2. 비관적 락으로 지갑 조회 - SELECT FOR UPDATE
        //    같은 userId 에 대한 동시 결제 요청을 직렬화
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new PaymentException(ErrorCode.WALLET_NOT_FOUND));

        // 4. 잔액 차감 (도메인 내부에서 잔액 부족 검증)
        wallet.decrease(request.getAmount());

        // 5. 결제 레코드 생성
        Payment payment = Payment.create(
                request.getOrderId(), request.getUserId(),
                request.getAmount(), request.getIdempotencyKey()
        );

        // 6. 외부 결제 API 호출 (가상 스레드로 처리 - 실제로는 I/O 블로킹)
        boolean externalSuccess = externalPaymentClient.process(payment);

        if (externalSuccess) {
            payment.complete();
            order.markPaid();
        } else {
            payment.fail();
            order.markFailed();
            // 실패 시 wallet.decrease() 는 트랜잭션 롤백으로 자동 원복
            throw new PaymentException(ErrorCode.PAYMENT_FAILED);
        }

        paymentRepository.save(payment);
        log.info("[Payment] SUCCESS userId={} amount={} key={}", request.getUserId(), request.getAmount(), request.getIdempotencyKey());

        // 7. 결제 완료 이벤트 발행 (비동기 알림 - 트랜잭션과 분리)
        //    PaymentNotificationService 가 customExecutor(가상 스레드)에서 비동기로 처리
        //    이메일 발송 실패가 결제 성공 응답에 영향을 주지 않음
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                this,
                payment.getId(),
                request.getUserId(),
                request.getAmount(),
                "user-" + request.getUserId() + "@example.com"  // 실제 서비스: UserRepository 로 이메일 조회
        ));

        return PaymentResponse.from(payment);
    }
}
