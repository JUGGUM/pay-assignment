package com.pay.practice.pay_assignment;

import com.pay.practice.pay_assignment.domain.*;
import com.pay.practice.pay_assignment.dto.PaymentRequest;
import com.pay.practice.pay_assignment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트
 *
 * 시나리오: 초기 잔액 100,000원인 지갑에 10개의 스레드가 동시에 10,000원 결제 시도
 * 기대값: 정확히 10번만 성공, 최종 잔액 0원
 *
 * CountDownLatch 사용 이유:
 * - startLatch: 모든 스레드가 준비된 후 동시에 시작 (Race Condition 재현)
 * - endLatch: 모든 스레드 완료를 기다린 후 검증
 *
 * 비관적 락이 없으면 발생하는 문제:
 * - Thread A, B 모두 잔액 100,000원을 읽음
 * - Thread A 10,000원 차감 후 저장 -> 90,000원
 * - Thread B도 10,000원 차감 후 저장 -> 90,000원 (A의 차감이 덮어씌워짐)
 * - 20,000원이 나가야 하는데 10,000원만 차감됨 = 잔액 불일치
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private OrderRepository orderRepository;

    private static final long USER_ID = 1L;
    private static final long INITIAL_BALANCE = 100_000L;
    private static final long PAY_AMOUNT = 10_000L;
    private static final int THREAD_COUNT = 10;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.save(Wallet.create(USER_ID, INITIAL_BALANCE));
    }

    @Test
    @DisplayName("동시에 10개 결제 요청 -> 비관적 락으로 정확히 10번 성공, 최종 잔액 0원")
    void concurrentPayment_pessimisticLock_shouldPreventRaceCondition() throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);   // 동시 시작 신호
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);  // 완료 대기

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // 각 스레드별 독립 주문 생성 (동시성 재현을 위해 미리 저장)
                    Order order = orderRepository.save(Order.create(USER_ID, PAY_AMOUNT));

                    startLatch.await();  // 모든 스레드가 준비될 때까지 대기

                    PaymentRequest request = buildRequest(order.getId(), index);
                    paymentService.pay(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 일제히 시작
        endLatch.await();        // 모든 스레드 완료 대기
        executor.shutdown();

        // 검증
        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();

        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.printf("성공: %d건, 실패: %d건%n", successCount.get(), failCount.get());
        System.out.printf("최종 잔액: %,d원 (기대: 0원)%n", wallet.getBalance());

        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(failCount.get()).isZero();
        assertThat(wallet.getBalance()).isZero();  // 10 * 10,000 = 100,000 전액 차감
    }

    @Test
    @DisplayName("잔액 초과 동시 결제 -> 일부만 성공, 잔액 음수 없음")
    void concurrentPayment_insufficientBalance_shouldNotGoBelowZero() throws InterruptedException {
        // 잔액 50,000원에 10개 스레드가 10,000원씩 요청 -> 5개만 성공해야 함
        walletRepository.deleteAll();
        walletRepository.save(Wallet.create(USER_ID, 50_000L));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Order order = orderRepository.save(Order.create(USER_ID, PAY_AMOUNT));
                    startLatch.await();
                    paymentService.pay(buildRequest(order.getId(), index));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();

        System.out.println("=== 잔액 초과 테스트 결과 ===");
        System.out.printf("성공: %d건%n", successCount.get());
        System.out.printf("최종 잔액: %,d원 (0 이상이어야 함)%n", wallet.getBalance());

        assertThat(wallet.getBalance()).isGreaterThanOrEqualTo(0L);  // 잔액은 절대 음수가 되면 안 됨
        assertThat(successCount.get()).isEqualTo(5);  // 5만원 / 1만원 = 5건
    }

    private PaymentRequest buildRequest(Long orderId, int index) throws Exception {
        // 리플렉션으로 private 필드 설정 (테스트용 간소화)
        PaymentRequest request = new PaymentRequest();
        var orderIdField = PaymentRequest.class.getDeclaredField("orderId");
        var userIdField = PaymentRequest.class.getDeclaredField("userId");
        var amountField = PaymentRequest.class.getDeclaredField("amount");
        var keyField = PaymentRequest.class.getDeclaredField("idempotencyKey");

        orderIdField.setAccessible(true); orderIdField.set(request, orderId);
        userIdField.setAccessible(true);  userIdField.set(request, USER_ID);
        amountField.setAccessible(true);  amountField.set(request, PAY_AMOUNT);
        keyField.setAccessible(true);     keyField.set(request, UUID.randomUUID().toString());

        return request;
    }
}
