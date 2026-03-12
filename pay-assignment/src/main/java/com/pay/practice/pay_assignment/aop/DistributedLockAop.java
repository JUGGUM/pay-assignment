package com.pay.practice.pay_assignment.aop;

import com.pay.practice.pay_assignment.exception.ErrorCode;
import com.pay.practice.pay_assignment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Redisson 분산 락 AOP
 * - 비관적 락: 단일 DB 인스턴스 환경에서 간단하고 강력
 * - 분산 락(Redisson): 다중 서버/DB 샤딩 환경에서 DB 커넥션 낭비 없이 제어
 * 이 Aspect는 @DistributedLock 어노테이션이 붙은 메서드에 자동 적용됨
 *
 * @ConditionalOnBean: Redis(Redisson) 가 없는 테스트 환경에서는 이 빈이 생성되지 않음
 * → 테스트 시 RedissonClient 빈 부재로 컨텍스트가 실패하는 것을 방지
 */
@Slf4j
@Aspect
@Component
@ConditionalOnBean(RedissonClient.class)
@RequiredArgsConstructor
public class DistributedLockAop {

    private static final String LOCK_PREFIX = "LOCK:";
    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.pay.practice.pay_assignment.aop.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        String lockKey = LOCK_PREFIX + resolveKey(annotation.key(), signature.getParameterNames(), joinPoint.getArgs());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(annotation.waitTime(), annotation.leaseTime(), annotation.timeUnit());
            if (!acquired) {
                // waitTime 초과 - 다른 요청이 락을 점유 중
                throw new PaymentException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }
            log.debug("[DistributedLock] acquired key={}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] released key={}", lockKey);
            }
        }
    }

    // SpEL 표현식 파싱: "#userId" -> 실제 인자 값
    private String resolveKey(String expression, String[] paramNames, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        return String.valueOf(parser.parseExpression(expression).getValue(context));
    }
}
