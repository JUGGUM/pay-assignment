package com.pay.practice.pay_assignment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PayAssignmentApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void 동시성_결제_테스트() throws InterruptedException {
		int threadCount = 100;
		// 가상 스레드를 사용해보고 싶다면 Executors.newVirtualThreadPerTaskExecutor()도 가능
		ExecutorService executorService = Executors.newFixedThreadPool(32);
		CountDownLatch latch = new CountDownLatch(threadCount);

		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				try {
					// 결제 API 호출 로직 위치
				} finally {
					latch.countDown();
				}
			});
		}
		latch.await();
		// 결과 검증 로직 위치
	}

}
