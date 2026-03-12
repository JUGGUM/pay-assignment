package com.pay.practice.pay_assignment.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 분산 락 어노테이션
 * - key: SpEL 표현식으로 락 키 지정 (예: "#userId")
 * - waitTime: 락 획득 대기 시간
 * - leaseTime: 락 보유 최대 시간 (자동 해제, 장애 시 데드락 방지)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();
    long waitTime() default 3L;
    long leaseTime() default 5L;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
