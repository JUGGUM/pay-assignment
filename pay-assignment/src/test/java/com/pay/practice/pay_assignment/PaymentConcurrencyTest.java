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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  비관적 락 없이 발생하는 Race Condition                               │
 * │                                                                      │
 * │  T1: read balance=100,000 ──────────────────── write balance=90,000  │
 * │  T2: read balance=100,000 ── write balance=90,000                    │
 * │                                                                      │
 * │  결과: 20,000원이 나가야 하는데 10,000원만 차감 (잔액 불일치)           │
 * │                                                                      │
 * │  비관적 락 적용 후:                                                   │
 * │  T1: SELECT FOR UPDATE → 차감 → 커밋                                  │
 * │  T2:                    ← 대기 → SELECT FOR UPDATE → 차감 → 커밋      │
 * │  결과: 정확히 20,000원 차감                                           │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * 테스트 흐름:
 * 1. CountDownLatch(startGate) 로 모든 스레드가 동시에 출발하도록 동기화
 * 2. CountDownLatch(endGate) 로 모든 스레드 완료 대기
 * 3. 최종 잔액 / 성공 건수로 정합성 검증
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    // ─────────────────────────────────────────────
    // 시나리오 1: 동시 결제 전액 처리
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[10 threads] 잔액 100,000 / 건당 10,000 → 모두 성공, 잔액 0원")
    void concurrentPay_10threads_allSuccess() throws InterruptedException {
        int threads = 10;
        long balance = 100_000L;
        long amount  = 10_000L;

        runConcurrentTest(threads, balance, amount, threads, 0, 0L);
    }

    // ─────────────────────────────────────────────
    // 시나리오 2: 잔액 초과 → 일부만 성공
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[10 threads] 잔액 50,000 / 건당 10,000 → 5건 성공, 잔액 0원, 음수 불가")
    void concurrentPay_10threads_partialSuccess() throws InterruptedException {
        int threads       = 10;
        long balance      = 50_000L;
        long amount       = 10_000L;
        int expectedOk    = 5;
        int expectedFail  = 5;
        long expectedLeft = 0L;

        runConcurrentTest(threads, balance, amount, expectedOk, expectedFail, expectedLeft);
    }

    // ─────────────────────────────────────────────
    // 시나리오 3: 고부하 - 50 스레드
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[50 threads] 잔액 500,000 / 건당 10,000 → 모두 성공, 잔액 0원")
    void concurrentPay_50threads_highLoad() throws InterruptedException {
        int threads = 50;
        long balance = 500_000L;
        long amount  = 10_000L;

        runConcurrentTest(threads, balance, amount, threads, 0, 0L);
    }

    // ─────────────────────────────────────────────
    // 시나리오 4: 잔액 딱 1건치 - 동시 100건 중 1건만 성공
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[100 threads] 잔액 10,000 / 건당 10,000 → 1건만 성공, 나머지 99건 잔액 부족")
    void concurrentPay_100threads_onlyOneSuccess() throws InterruptedException {
        // 잔액이 딱 1건치만 있으므로, 비관적 락으로 직렬화된 후 첫 번째 요청만 성공
        // 나머지 99건은 잔액 부족(INSUFFICIENT_BALANCE) 예외
        runConcurrentTest(100, 10_000L, 10_000L, 1, 99, 0L);
    }

    // ─────────────────────────────────────────────
    // 시나리오 5: 멱등성 - 동일 key 중복 요청
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[10 threads] 동일 idempotencyKey 동시 요청 → 1건만 성공")
    void concurrentPay_sameIdempotencyKey_onlyOneSuccess() throws InterruptedException {
        walletRepository.save(Wallet.create(1L, 100_000L));
        Order order = orderRepository.save(Order.create(1L, 10_000L));
        String SAME_KEY = UUID.randomUUID().toString();

        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(threads);
        AtomicInteger success    = new AtomicInteger();

        // 가상 스레드 풀로 실행 (Java 21)
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    paymentService.pay(PaymentRequest.of(order.getId(), 1L, 10_000L, SAME_KEY));
                    success.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        executor.shutdown();

        // 동일 key 로는 1건만 성공
        assertThat(success.get()).isEqualTo(1);
        assertThat(paymentRepository.findByIdempotencyKey(SAME_KEY)).isPresent();

        // 잔액도 1번만 차감
        Wallet wallet = walletRepository.findByUserId(1L).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(100_000L - 10_000L);
    }

    // ─────────────────────────────────────────────
    // 공통 실행 유틸
    // ─────────────────────────────────────────────
    private void runConcurrentTest(
            int threads, long initialBalance, long payAmount,
            int expectedSuccess, int expectedFail, long expectedFinalBalance
    ) throws InterruptedException {

        final long userId = 1L;
        walletRepository.save(Wallet.create(userId, initialBalance));

        // 스레드마다 고유 주문 미리 생성 (Order 는 동시성 대상 아님)
        List<Order> orders = IntStream.range(0, threads)
                .mapToObj(i -> orderRepository.save(Order.create(userId, payAmount)))
                .toList();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(threads);
        AtomicInteger success    = new AtomicInteger();
        AtomicInteger fail       = new AtomicInteger();

        // 가상 스레드 익스큐터 - OS 스레드 낭비 없이 수백 개 동시 처리 가능
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threads; i++) {
            final Long orderId = orders.get(i).getId();
            executor.submit(() -> {
                try {
                    startGate.await();  // 동시 출발 동기화
                    paymentService.pay(PaymentRequest.of(orderId, userId, payAmount, UUID.randomUUID().toString()));
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();  // 전 스레드 동시 출발
        endGate.await();        // 모든 스레드 완료 대기
        executor.shutdown();

        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();

        System.out.printf("=== 동시성 테스트 결과 [threads=%d] ===%n", threads);
        System.out.printf("  성공: %d건 / 실패: %d건 / 최종 잔액: %,d원%n",
                success.get(), fail.get(), wallet.getBalance());

        // ✅ 핵심 검증
        assertThat(success.get()).as("성공 건수").isEqualTo(expectedSuccess);
        assertThat(fail.get()).as("실패 건수").isEqualTo(expectedFail);
        assertThat(wallet.getBalance()).as("최종 잔액").isEqualTo(expectedFinalBalance);
        assertThat(wallet.getBalance()).as("잔액 음수 불가").isGreaterThanOrEqualTo(0L);
    }
}
