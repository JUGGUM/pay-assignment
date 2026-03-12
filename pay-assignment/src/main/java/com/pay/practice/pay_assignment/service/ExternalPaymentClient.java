package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.domain.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 외부 결제 API 모킹
 * - 실제 환경에서는 RestClient/WebClient 로 PG사 API 호출
 * - 가상 스레드(Virtual Thread)는 Thread.sleep() 같은 블로킹 I/O 시에도
 *   OS 스레드(캐리어 스레드)를 점유하지 않고 park/unpark 처리
 * - 덕분에 소수의 OS 스레드로 수천 개의 동시 결제 요청 처리 가능
 */
@Slf4j
@Component
public class ExternalPaymentClient {

    public boolean process(Payment payment) {
        try {
            // 외부 API 응답 지연 시뮬레이션 (100ms)
            // 가상 스레드: 이 블로킹 구간에서 캐리어 스레드 반환 -> 다른 가상 스레드 실행
            Thread.sleep(100);

            log.debug("[ExternalPG] processed paymentId={} amount={}", payment.getId(), payment.getAmount());
            return true;  // 실제 구현 시 HTTP 응답 코드로 판단

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
