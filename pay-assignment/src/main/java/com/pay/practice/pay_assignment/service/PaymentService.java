package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.dto.PaymentResponse;
import com.pay.practice.pay_assignment.event.PaymentCancelledEvent;
import com.pay.practice.pay_assignment.event.PaymentCompletedEvent;
import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 비관적 락(PESSIMISTIC_WRITE) 기반 결제 서비스
 *
 * 선택 이유:
 * - 금융 시스템은 충돌 가능성이 높고, 충돌 시 재처리 비용이 큼
 * - 낙관적 락은 재시도 로직 필요 + 사용자 경험 저하
 * - 비관적 락은 DB 트랜잭션 범위 내에서 순서를 보장하므로 잔액/재고 정합성에 적합
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
    private final OrderRepository orderRepository;
    private final ExternalPaymentClient externalPaymentClient;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentValidator validator;    // 검증 로직 위임
    private final ProductService productService; // 재고 처리 위임

    @Transactional
    public PaymentResponse pay(PaymentRequest request) {

        // 1. 멱등성 + 주문 상태 검증 (PaymentValidator 에 위임)
        validator.validateIdempotency(request.getIdempotencyKey());
        Order order = validator.validateOrder(request.getOrderId());

        // 2. 비관적 락으로 지갑 조회 - SELECT FOR UPDATE
        //    같은 userId 에 대한 동시 결제 요청을 직렬화
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new PaymentException(ErrorCode.WALLET_NOT_FOUND));

        // 3. 잔액 차감 (도메인 내부에서 잔액 부족 검증)
        wallet.decrease(request.getAmount());

        // 4. 재고 차감 (상품 연동 시)
        //    ProductService.decrease()는 REQUIRED 전파로 이 트랜잭션에 합류
        //    → 이후 단계에서 예외 발생 시 잔액 차감과 함께 재고 차감도 자동 롤백
        if (request.getProductId() != null) {
            int qty = request.getQuantity() != null ? request.getQuantity() : 1;
            productService.decrease(request.getProductId(), qty);
        }

        // 5. 결제 레코드 생성
        Payment payment = Payment.create(
                request.getOrderId(), request.getUserId(),
                request.getAmount(), request.getIdempotencyKey()
        );
        if (request.getProductId() != null) {
            payment.attachProduct(request.getProductId(),
                    request.getQuantity() != null ? request.getQuantity() : 1);
        }

        // 6. 외부 결제 API 호출 (가상 스레드로 처리 - 실제로는 I/O 블로킹)
        boolean externalSuccess = externalPaymentClient.process(payment);

        if (externalSuccess) {
            payment.complete();
            order.markPaid();
        } else {
            payment.fail();
            order.markFailed();
            // 실패 시 wallet.decrease() / productService.decrease() 는 트랜잭션 롤백으로 자동 원복
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

    /**
     * 결제 취소
     *
     * 비관적 락으로 Payment를 먼저 조회하는 이유:
     * 동일 paymentId로 동시 취소 요청이 들어올 경우,
     * 두 번째 요청은 첫 번째 커밋 후 CANCELLED 상태를 보고 PAYMENT_ALREADY_CANCELLED 예외를 받음
     * → 중복 환불 방지
     *
     * 처리 순서가 중요한 이유:
     * 1. Payment 취소 (상태 검증 포함)
     * 2. Order 취소
     * 3. 지갑 복구 (비관적 락)
     * 4. 재고 복구 (비관적 락)
     * → 모두 동일 트랜잭션 안에서 실행되므로 부분 성공 없음
     */
    @Transactional
    public PaymentResponse cancel(Long paymentId) {

        // 1. 비관적 락으로 결제 조회 (동시 취소 요청 직렬화)
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. Payment 상태 전이 검증 및 취소
        //    CANCELLED → 예외, FAILED → 예외(환불 대상 아님), SUCCESS → CANCELLED 허용
        payment.cancel();

        // 3. Order 취소 (PAID → CANCELLED)
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new PaymentException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel();

        // 4. 지갑 잔액 복구
        Wallet wallet = walletRepository.findByUserIdWithLock(payment.getUserId())
                .orElseThrow(() -> new PaymentException(ErrorCode.WALLET_NOT_FOUND));
        wallet.increase(payment.getAmount());

        // 5. 재고 복구 (결제 시 상품이 포함된 경우)
        if (payment.getProductId() != null) {
            productService.increase(payment.getProductId(), payment.getQuantity());
        }

        log.info("[Payment] CANCELLED paymentId={} userId={} amount={}",
                paymentId, payment.getUserId(), payment.getAmount());

        // 6. 취소 이벤트 발행 (환불 알림 등 비동기 처리)
        eventPublisher.publishEvent(new PaymentCancelledEvent(
                this, payment.getId(), payment.getUserId(), payment.getAmount()
        ));

        return PaymentResponse.from(payment);
    }

    /**
     * 결제 단건 조회
     * 읽기 전용 트랜잭션으로 불필요한 더티 체킹 비용 제거
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponse.from(payment);
    }

    /**
     * 주문별 결제 목록 조회
     * 재결제/부분결제 이력이 있는 경우 여러 건이 반환될 수 있음
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }
}
