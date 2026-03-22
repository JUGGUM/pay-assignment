package com.pay.practice.pay_assignment.config;

import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * 테스트용 Redisson 설정
 *
 * 왜 Mock Bean을 제공하는가:
 * - @SpringBootTest 는 전체 ApplicationContext 를 로드하므로 Redisson 스타터가 활성화됨
 * - RedissonAutoConfigurationV2 는 @ConditionalOnMissingBean(RedissonClient.class) 를 사용
 * - 여기서 mock RedissonClient 를 먼저 등록하면 Redisson 자동 설정이 실제 연결 시도를 건너뜀
 * - DistributedLockAop 는 @ConditionalOnBean(RedissonClient.class) 로 mock 빈을 인식하지만
 *   통합 테스트에서는 DistributedLockPaymentService 를 사용하지 않으므로 문제없음
 */
@TestConfiguration
@Profile("test")
public class TestRedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        return Mockito.mock(RedissonClient.class);
    }
}
