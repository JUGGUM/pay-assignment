package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 결제 완료 알림 서비스
 *
 * @Async("customExecutor"): AsyncConfig 의 가상 스레드 익스큐터에서 비동기 실행
 *   - 결제 트랜잭션과 분리: 이메일 발송 실패가 결제 결과에 영향 없음
 *   - pay() 메서드는 이벤트 발행 후 즉시 응답 반환
 *
 * @EventListener: 동기 이벤트 리스너를 @Async 와 조합하면 별도 스레드에서 처리
 *   (TransactionalEventListener 로 교체 시 트랜잭션 커밋 후 실행 보장 가능)
 *
 * @Order: 여러 리스너가 있을 때 실행 순서 지정 (숫자 낮을수록 먼저)
 */
@Slf4j
@Service
public class PaymentNotificationService {

    @Async("customExecutor")
    @EventListener
    @Order(1)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[PaymentNotification] 결제 알림 처리 시작 - paymentId={}, userId={}, amount={}",
                event.getPaymentId(), event.getUserId(), event.getAmount());

        try {
            // 실제 구현 시 EmailSenderService 주입 후 아래 로직 적용
            // emailSenderService.sendEmail(
            //     event.getRecipientEmail(),
            //     "[알림] 결제가 완료되었습니다.",
            //     "payment/payment-completed",
            //     event.getCompletedAt(),
            //     Map.of(
            //         "paymentId", event.getPaymentId(),
            //         "amount", event.getAmount()
            //     ),
            //     "결제서비스"
            // );

            // 모킹: 외부 발송 지연 시뮬레이션 (가상 스레드에서 블로킹해도 OS 스레드 낭비 없음)
            Thread.sleep(200);

            log.info("[PaymentNotification] 결제 알림 발송 완료 - recipient={}, completedAt={}",
                    event.getRecipientEmail(), event.getCompletedAt());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[PaymentNotification] 알림 처리 중단 - paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            // 알림 실패는 결제 성공에 영향 없음 - 로그 후 계속
            // 실제 운영: Slack 웹훅 / DB 실패 기록 / 재발송 큐 등 연동
            log.error("[PaymentNotification] 알림 발송 실패 - paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage());
        }
    }
}
