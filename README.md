# Payment System Base Project

결제 시스템 사전과제 대비용 베이스 프로젝트입니다.  
**Java 21 + Spring Boot 3.2 + JPA + Redis(Redisson)** 기반의 동시성 안전 결제 처리 구조를 담고 있습니다.

---

## 기술 스택

| 항목 | 선택 |
|---|---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.2 |
| DB | H2 (테스트), JPA/Hibernate |
| Cache/Lock | Redis + Redisson 3.27 |
| Build | Gradle |

---

## 핵심 설계 의도

### 1. 왜 비관적 락(Pessimistic Lock)인가?

금융 시스템에서 잔액 차감은 **충돌 발생 확률이 높고, 충돌 시 재처리 비용이 큰** 작업입니다.

```
낙관적 락 흐름:
  Thread A, B 동시에 잔액 읽기 → 각자 차감 시도 → 버전 충돌 → 재시도
  → 재시도 횟수 제한 없음, 사용자 경험 저하, 오류 처리 복잡

비관적 락 흐름:
  Thread A 가 SELECT FOR UPDATE 획득 → 잔액 차감 → 커밋
  → Thread B 는 대기 → A 완료 후 B 진행 → 정확한 직렬화 보장
```

`WalletRepository.findByUserIdWithLock()` 이 `SELECT ... FOR UPDATE` 를 실행하여  
동일 사용자에 대한 동시 결제를 DB 레벨에서 직렬화합니다.

### 2. 분산 락(Redisson)은 언제 쓰는가?

비관적 락의 한계: **DB 커넥션 점유 + 단일 DB 인스턴스 의존**

다중 서버 또는 DB 샤딩 환경에서는 Redisson의 분산 락이 필요합니다.

```java
// AOP 방식: 비즈니스 로직과 락 로직 분리
@DistributedLock(key = "'PAY:' + #request.userId", waitTime = 3, leaseTime = 5)
public PaymentResponse pay(PaymentRequest request) { ... }
```

- `leaseTime` 으로 프로세스 크래시 시 자동 락 해제 (데드락 방지)
- SpEL 표현식으로 메서드 인자 기반 동적 키 생성

### 3. 가상 스레드(Virtual Threads)가 주는 이점

```yaml
spring.threads.virtual.enabled: true
```

기존 플랫폼 스레드는 I/O 대기 중 OS 스레드를 점유합니다.  
가상 스레드는 블로킹 I/O 시 **캐리어 스레드(OS 스레드)를 반환**하고 park 상태로 전환됩니다.

결제 흐름에서 I/O 구간:
- DB 락 대기 (SELECT FOR UPDATE)
- 외부 PG사 API 호출 (100ms~수초)
- Redis 분산 락 획득 대기

→ 기존 200개 스레드 풀 대비, 수천 개의 동시 결제 요청을 적은 OS 스레드로 처리 가능

### 4. 멱등성(Idempotency) 처리

네트워크 오류로 클라이언트가 동일 요청을 재전송하면 중복 결제가 발생합니다.

```
클라이언트: idempotencyKey = "uuid-abc-123" 로 결제 요청
서버: 이미 처리된 키 → 기존 결제 결과 반환 (재처리 없음)
```

`Payment.idempotencyKey` 에 `unique` 인덱스를 걸어 DB 레벨에서도 중복을 차단합니다.

---

## 패키지 구조

```
src/main/java/com/pay/practice/pay_assignment/
├── aop/
│   ├── DistributedLock.java          # 분산 락 커스텀 어노테이션
│   └── DistributedLockAop.java       # Redisson AOP Aspect
├── controller/
│   └── PaymentController.java        # POST /api/v1/payments
├── domain/
│   ├── Order.java / OrderRepository.java
│   ├── Payment.java / PaymentRepository.java
│   └── Wallet.java / WalletRepository.java  # findByUserIdWithLock (비관적 락)
├── dto/
│   ├── PaymentRequest.java
│   └── PaymentResponse.java
├── exception/
│   ├── ErrorCode.java                # 금융 도메인 에러 코드 (PAY-001 등)
│   ├── ErrorResponse.java
│   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   └── PaymentException.java
└── service/
    ├── ExternalPaymentClient.java    # 외부 PG API 모킹
    ├── PaymentService.java           # 비관적 락 방식
    └── DistributedLockPaymentService.java  # Redisson 분산 락 방식
```

---

## 동시성 테스트 실행

```bash
# Redis 없이 비관적 락 테스트만 실행
./gradlew test --tests "*ConcurrencyTest*" -Dspring.profiles.active=test
```

테스트 시나리오:
1. 잔액 100,000원 지갑에 10개 스레드가 10,000원 동시 결제 → 모두 성공, 최종 잔액 0원
2. 잔액 50,000원에 10개 스레드 → 5개만 성공, 잔액 절대 음수 없음

---

## 로컬 실행 (Redis 포함)

```bash
# Redis 실행 (Docker)
docker run -d -p 6379:6379 redis:7

# 애플리케이션 실행
./gradlew bootRun
```

---

## 에러 코드 체계

| 코드 | HTTP | 설명 |
|---|---|---|
| PAY-001 | 422 | 잔액 부족 |
| PAY-002 | 404 | 지갑 없음 |
| PMT-002 | 409 | 이미 처리된 결제 |
| PMT-003 | 409 | 중복 idempotencyKey |
| LCK-001 | 429 | 락 획득 실패 (트래픽 과부하) |
| CMN-001 | 400 | 입력값 오류 |
