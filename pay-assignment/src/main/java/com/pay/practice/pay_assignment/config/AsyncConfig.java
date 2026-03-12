package com.pay.practice.pay_assignment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 비동기 처리 설정
 *
 * customExecutor 를 가상 스레드(Virtual Thread) 기반으로 구성
 * - 기존: ThreadPoolTaskExecutor (고정 크기 풀, 플랫폼 스레드)
 * - 변경: newVirtualThreadPerTaskExecutor (요청마다 새 가상 스레드 생성)
 *
 * 이메일/알림처럼 I/O 대기가 긴 비동기 작업에서 가상 스레드가 유리한 이유:
 * - 플랫폼 스레드는 SMTP 응답 대기 중 OS 스레드를 점유 (풀 고갈 위험)
 * - 가상 스레드는 대기 중 캐리어 스레드를 반환 → 수천 건 동시 알림도 안전
 * - 별도 풀 크기 튜닝 불필요 (JVM이 자동 스케줄링)
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "customExecutor")
    public Executor customExecutor() {
        // Java 21: 가상 스레드 퍼-태스크 익스큐터
        // @Async("customExecutor") 가 붙은 메서드는 각각 별도 가상 스레드에서 실행
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
