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
 * 상품 재고 동시성 통합 테스트
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  비관적 락 없이 발생하는 재고 Race Condition                          │
 * │                                                                      │
 * │  T1: read stock=1 ──────────────────── write stock=0                 │
 * │  T2: read stock=1 ── write stock=0                                   │
 * │                                                                      │
 * │  결과: 재고 1개인데 2건이 성공 → 재고 음수 발생                       │
 * │                                                                      │
 * │  비관적 락(findByIdWithLock) 적용 후:                                 │
 * │  T1: SELECT FOR UPDATE → 차감(stock=0) → 커밋                        │
 * │  T2:                   ← 대기 → SELECT FOR UPDATE → 재고 부족 예외   │
 * │  결과: 1건만 성공, 재고 음수 불가                                     │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * pay() 트랜잭션 안에서 ProductService.decrease()가 실행되므로
 * 재고 차감과 잔액 차감이 원자적으로 처리됨
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductConcurrencyTest {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProductRepository productRepository;

    private static final Long USER_ID = 99L;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        walletRepository.deleteAll();
    }

    // ─────────────────────────────────────────────
    // 시나리오 1: 재고 1개 상품에 동시 50건 → 1건만 성공
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[50 threads] 재고 1개 상품에 동시 50건 → 1건만 성공, 재고 음수 불가")
    void concurrentDecrease_stock1_onlyOneSuccess() throws InterruptedException {
        int threads = 50;
        long unitPrice = 10_000L;

        // 지갑 잔액은 충분하게 설정 (재고 부족이 병목이 되어야 하므로)
        walletRepository.save(Wallet.create(USER_ID, unitPrice * threads));
        Product product = productRepository.save(Product.create("한정판 상품", 1, unitPrice));

        // 스레드마다 고유 주문 생성
        List<Order> orders = IntStream.range(0, threads)
                .mapToObj(i -> orderRepository.save(Order.create(USER_ID, unitPrice)))
                .toList();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(threads);
        AtomicInteger success    = new AtomicInteger();
        AtomicInteger fail       = new AtomicInteger();

        // 가상 스레드로 동시 요청 - OS 스레드 절약하면서 대규모 동시성 시뮬레이션
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threads; i++) {
            final Long orderId = orders.get(i).getId();
            executor.submit(() -> {
                try {
                    startGate.await();  // 전 스레드 동시 출발
                    paymentService.pay(PaymentRequest.of(
                            orderId, USER_ID, unitPrice,
                            UUID.randomUUID().toString(),
                            product.getId(), 1
                    ));
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        executor.shutdown();

        Product afterProduct = productRepository.findById(product.getId()).orElseThrow();

        System.out.printf("=== 재고 동시성 테스트 결과 [threads=%d] ===%n", threads);
        System.out.printf("  성공: %d건 / 실패: %d건 / 최종 재고: %d개%n",
                success.get(), fail.get(), afterProduct.getStock());

        // ✅ 핵심 검증
        assertThat(success.get()).as("재고 1개이므로 1건만 성공").isEqualTo(1);
        assertThat(fail.get()).as("나머지 49건은 재고 부족으로 실패").isEqualTo(threads - 1);
        assertThat(afterProduct.getStock()).as("재고 음수 불가").isZero();
        assertThat(afterProduct.getStock()).as("재고가 0 이상").isGreaterThanOrEqualTo(0);
    }

    // ─────────────────────────────────────────────
    // 시나리오 2: 재고 10개 상품에 동시 30건 → 10건 성공
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("[30 threads] 재고 10개 상품에 동시 30건 → 10건 성공, 재고 0개")
    void concurrentDecrease_stock10_tenSuccess() throws InterruptedException {
        int threads = 30;
        int initialStock = 10;
        long unitPrice = 5_000L;

        walletRepository.save(Wallet.create(USER_ID, unitPrice * threads));
        Product product = productRepository.save(Product.create("인기 상품", initialStock, unitPrice));

        List<Order> orders = IntStream.range(0, threads)
                .mapToObj(i -> orderRepository.save(Order.create(USER_ID, unitPrice)))
                .toList();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(threads);
        AtomicInteger success    = new AtomicInteger();
        AtomicInteger fail       = new AtomicInteger();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threads; i++) {
            final Long orderId = orders.get(i).getId();
            executor.submit(() -> {
                try {
                    startGate.await();
                    paymentService.pay(PaymentRequest.of(
                            orderId, USER_ID, unitPrice,
                            UUID.randomUUID().toString(),
                            product.getId(), 1
                    ));
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        executor.shutdown();

        Product afterProduct = productRepository.findById(product.getId()).orElseThrow();

        System.out.printf("=== 재고 동시성 테스트 결과 [threads=%d, stock=%d] ===%n", threads, initialStock);
        System.out.printf("  성공: %d건 / 실패: %d건 / 최종 재고: %d개%n",
                success.get(), fail.get(), afterProduct.getStock());

        assertThat(success.get()).as("재고 10개이므로 10건 성공").isEqualTo(initialStock);
        assertThat(fail.get()).as("나머지 20건 재고 부족").isEqualTo(threads - initialStock);
        assertThat(afterProduct.getStock()).as("재고 정확히 0개").isZero();
    }
}
