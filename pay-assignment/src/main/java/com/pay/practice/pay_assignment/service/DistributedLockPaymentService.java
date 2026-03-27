package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.aop.DistributedLock;
import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.config.error.ErrorCode;
import com.pay.practice.pay_assignment.config.error.exception.BadRequestException;
import com.pay.practice.pay_assignment.config.error.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



/**
 * Redisson 분산 락 기반 결제 서비스 (비교용)
 *
 * 비관적 락 vs 분산 락:
 * - 비관적 락: DB 트랜잭션에 종속, 단일 DB에서 강력
 * - 분산 락: DB 커넥션 없이 Redis에서 제어, 다중 인스턴스/DB 샤딩 환경에 적합
 *             단, Redis 장애 시 fallback 전략 필요
 *
 * 주의: @DistributedLock 은 AOP 프록시를 통해 동작하므로
 *       같은 클래스 내 자기 호출(self-invocation)은 락이 적용되지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockPaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final ExternalPaymentClient externalPaymentClient;
    private final PaymentValidator validator;  // 검증 로직 위임

    // key = "PAY:{userId}" 로 사용자 단위 락 적용
    // waitTime=3s: 락 대기 최대 3초, leaseTime=5s: 5초 후 자동 해제 (데드락 방지)
    @DistributedLock(key = "'PAY:' + #request.userId")
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {

        // 멱등성 + 주문 상태 검증
        validator.validateIdempotency(request.getIdempotencyKey());
        Order order = validator.validateOrder(request.getOrderId());

        // 분산 락이 이미 동시성을 제어하므로 일반 조회 사용 (DB 락 불필요)
        Wallet wallet = walletRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.WALLET_NOT_FOUND));

        wallet.decrease(request.getAmount());

        Payment payment = Payment.create(
                request.getOrderId(), request.getUserId(),
                request.getAmount(), request.getIdempotencyKey()
        );

        boolean externalSuccess = externalPaymentClient.process(payment);

        if (externalSuccess) {
            payment.complete();
            order.markPaid();
        } else {
            payment.fail();
            throw new BadRequestException(ErrorCode.PAYMENT_FAILED);
        }

        paymentRepository.save(payment);
        log.info("[DistributedLock Payment] SUCCESS userId={} amount={}", request.getUserId(), request.getAmount());

        return PaymentResponse.from(payment);
    }
}
