# Payment System Base Project

**Java 21 + Spring Boot 3.2 + JPA + Redis(Redisson)** 기반의 동시성 안전 결제 처리 구조를 담고 있습니다.

---

## 빠른 참조 (Quick Reference)

### 동시성 제어

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 비관적 락 - 지갑 | [WalletRepository.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/WalletRepository.java) | `findByUserIdWithLock()` — `@Lock(PESSIMISTIC_WRITE)` |
| 비관적 락 - 재고 | [ProductRepository.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/ProductRepository.java) | `findByIdWithLock()` — `@Lock(PESSIMISTIC_WRITE)` |
| 비관적 락 - 취소 | [PaymentRepository.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/PaymentRepository.java) | `findByIdWithLock()` — 동시 취소 요청 직렬화 |
| 비관적 락 사용 서비스 | [PaymentService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentService.java) | `pay()` / `cancel()` |
| 분산 락 어노테이션 정의 | [DistributedLock.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/aop/DistributedLock.java) | `key`, `waitTime`, `leaseTime` 속성 |
| 분산 락 AOP 구현 | [DistributedLockAop.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/aop/DistributedLockAop.java) | `lock()` — SpEL 파싱 + `tryLock` / `unlock` |
| 분산 락 사용 서비스 | [DistributedLockPaymentService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/DistributedLockPaymentService.java) | `@DistributedLock(key = "'PAY:' + #request.userId")` |

### 상태 전이

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 주문 상태 전이 검증 | [Order.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/Order.java) | `OrderStatus.canTransitionTo()` |
| 결제 상태 전이 검증 | [Payment.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/Payment.java) | `PaymentStatus.canTransitionTo()` |

### 멱등성 / 검증

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 멱등성 키 중복 체크 | [PaymentValidator.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentValidator.java) | `validateIdempotency()` |
| 주문 상태 검증 | [PaymentValidator.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentValidator.java) | `validateOrder()` |
| 멱등성 키 DB 인덱스 | [Payment.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/Payment.java) | `@Index(... unique = true)` on `idempotencyKey` |

### 재고 관리

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 재고 엔티티 | [Product.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/domain/Product.java) | `decrease()` / `increase()` — 도메인 내부 재고 검증 |
| 재고 서비스 | [ProductService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/ProductService.java) | `decrease()` / `increase()` — 결제 트랜잭션에 합류 |

### 비동기 / 이벤트

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 결제 완료 이벤트 발행 | [PaymentService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentService.java) | `eventPublisher.publishEvent(new PaymentCompletedEvent(...))` |
| 결제 취소 이벤트 발행 | [PaymentService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentService.java) | `eventPublisher.publishEvent(new PaymentCancelledEvent(...))` |
| 이벤트 클래스 정의 | [PaymentCompletedEvent.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/event/PaymentCompletedEvent.java) | `ApplicationEvent` 상속 구조 |
| 비동기 이벤트 리스너 | [PaymentNotificationService.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/service/PaymentNotificationService.java) | `@Async("customExecutor") @EventListener @Order(1)` |
| 가상 스레드 Executor 설정 | [AsyncConfig.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/config/AsyncConfig.java) | `Executors.newVirtualThreadPerTaskExecutor()` |

### 예외 처리

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 에러 코드 목록 | [ErrorCode.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/common/error/ErrorCode.java) | `PAY / ORD / PMT / PRD / LCK / CMN` 코드 체계 |
| 글로벌 예외 핸들러 | [GlobalExceptionHandler.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/common/error/GlobalExceptionHandler.java) | `@RestControllerAdvice` + `@ExceptionHandler` |
| 도메인 예외 클래스 | [PaymentException.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/common/error/exception/PaymentException.java) | `ErrorCode` 포함 RuntimeException |
| 에러 응답 포맷 | [ErrorResponse.java](pay-assignment/src/main/java/com/pay/practice/pay_assignment/common/error/ErrorResponse.java) | `code / message / timestamp / errors` (record) |

### 테스트

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| Mockito 단위 테스트 패턴 | [PaymentServiceUnitTest.java](pay-assignment/src/test/java/com/pay/practice/pay_assignment/PaymentServiceUnitTest.java) | `@Nested` + `given/when/then` BDD 구조 |
| 지갑 동시성 테스트 | [PaymentConcurrencyTest.java](pay-assignment/src/test/java/com/pay/practice/pay_assignment/PaymentConcurrencyTest.java) | `CountDownLatch` + `ExecutorService` + `AtomicInteger` |
| 재고 동시성 테스트 | [ProductConcurrencyTest.java](pay-assignment/src/test/java/com/pay/practice/pay_assignment/ProductConcurrencyTest.java) | 재고 1개 동시 50건 → 1건만 성공 |
| 통합 테스트 (H2 + 롤백 검증) | [PaymentServiceIntegrationTest.java](pay-assignment/src/test/java/com/pay/practice/pay_assignment/PaymentServiceIntegrationTest.java) | 잔액 차감 / 트랜잭션 롤백 / 멱등성 검증 |
| 테스트용 Redisson Mock | [TestRedissonConfig.java](pay-assignment/src/test/java/com/pay/practice/pay_assignment/config/TestRedissonConfig.java) | Redis 없이 Spring Context 로드 |

### 설정

| 필요한 것 | 파일 | 핵심 위치 |
|---|---|---|
| 가상 스레드 활성화 | [application.yaml](pay-assignment/src/main/resources/application.yaml) | `spring.threads.virtual.enabled: true` |
| Redis 연결 설정 | [redisson.yaml](pay-assignment/src/main/resources/redisson.yaml) | `singleServerConfig.address` |
| 테스트용 Redis 제외 설정 | [application-test.yaml](pay-assignment/src/test/resources/application-test.yaml) | `spring.autoconfigure.exclude` + mock RedissonClient |

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

## API 명세

### 결제 요청
```
POST /api/v1/payments
```
```json
{
  "orderId": 1,
  "userId": 1,
  "amount": 10000,
  "idempotencyKey": "uuid-abc-123",
  "productId": 1,      // optional
  "quantity": 1        // optional, default 1
}
```

### 결제 취소
```
DELETE /api/v1/payments/{paymentId}
```

### 결제 단건 조회
```
GET /api/v1/payments/{paymentId}
```

### 주문별 결제 목록 조회
```
GET /api/v1/payments?orderId={orderId}
```

---

## 핵심 설계 의도

### 1. 왜 비관적 락(Pessimistic Lock)인가?

금융 시스템에서 잔액/재고 차감은 **충돌 발생 확률이 높고, 충돌 시 재처리 비용이 큰** 작업입니다.

```
낙관적 락 흐름:
  Thread A, B 동시에 잔액 읽기 → 각자 차감 시도 → 버전 충돌 → 재시도
  → 재시도 횟수 제한 없음, 사용자 경험 저하, 오류 처리 복잡

비관적 락 흐름:
  Thread A 가 SELECT FOR UPDATE 획득 → 잔액 차감 → 커밋
  → Thread B 는 대기 → A 완료 후 B 진행 → 정확한 직렬화 보장
```

`WalletRepository.findByUserIdWithLock()` / `ProductRepository.findByIdWithLock()` 이 `SELECT ... FOR UPDATE` 를 실행하여 동시 요청을 DB 레벨에서 직렬화합니다.

### 2. 상태 전이 검증 (State Machine)

도메인 객체가 허용되지 않은 상태로 전이되는 것을 방지합니다.

```
OrderStatus:  PENDING → PAID → CANCELLED
                     → FAILED (단말)
                     → CANCELLED

PaymentStatus: PENDING → SUCCESS → CANCELLED
                       → FAILED (단말, 환불 대상 아님)
```

`canTransitionTo()` 메서드로 각 상태에서 허용된 전이만 통과시키고, 그 외는 `INVALID_STATUS_TRANSITION` 예외를 발생시킵니다.

### 3. 결제 취소의 원자성 보장

취소 처리는 여러 도메인을 동시에 변경합니다.

```
cancel() 트랜잭션:
  1. Payment 상태 → CANCELLED  (비관적 락, 중복 취소 방지)
  2. Order 상태   → CANCELLED
  3. Wallet 잔액  → 복구
  4. Product 재고 → 복구 (상품 연동 시)
  5. PaymentCancelledEvent 발행
```

`@Transactional` 하나로 묶어 부분 성공 없이 전체 성공 또는 전체 롤백을 보장합니다.

### 4. 분산 락(Redisson)은 언제 쓰는가?

비관적 락의 한계: **DB 커넥션 점유 + 단일 DB 인스턴스 의존**

다중 서버 또는 DB 샤딩 환경에서는 Redisson의 분산 락이 필요합니다.

```java
// AOP 방식: 비즈니스 로직과 락 로직 분리
@DistributedLock(key = "'PAY:' + #request.userId", waitTime = 3, leaseTime = 5)
public PaymentResponse pay(PaymentRequest request) { ... }
```

- `leaseTime` 으로 프로세스 크래시 시 자동 락 해제 (데드락 방지)
- SpEL 표현식으로 메서드 인자 기반 동적 키 생성

### 5. 가상 스레드(Virtual Threads)가 주는 이점

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

### 6. 멱등성(Idempotency) 처리

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
│   └── PaymentController.java        # POST, DELETE, GET /api/v1/payments
├── domain/
│   ├── Order.java / OrderRepository.java
│   ├── Payment.java / PaymentRepository.java  # findByIdWithLock (취소 락)
│   ├── Product.java / ProductRepository.java  # findByIdWithLock (재고 락)
│   └── Wallet.java / WalletRepository.java    # findByUserIdWithLock (잔액 락)
├── common/
│   └── error/
│       ├── ErrorCode.java            # 에러 코드 (PAY / ORD / PMT / PRD / LCK / CMN)
│       ├── ErrorResponse.java        # record 기반 에러 응답
│       ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│       └── exception/
│           └── PaymentException.java
├── dto/
│   ├── PaymentRequest.java           # productId / quantity 옵션 포함
│   └── PaymentResponse.java
├── event/
│   ├── PaymentCompletedEvent.java
│   └── PaymentCancelledEvent.java
└── service/
    ├── ExternalPaymentClient.java    # 외부 PG API 모킹
    ├── PaymentService.java           # 결제 / 취소 / 조회 (비관적 락)
    ├── DistributedLockPaymentService.java  # 결제 (Redisson 분산 락)
    ├── PaymentValidator.java         # 멱등성 / 주문 검증 분리 (SRP)
    └── ProductService.java           # 재고 차감 / 복구
```

---

## 동시성 테스트 실행

```bash
# Redis 없이 비관적 락 테스트 전체 실행
./gradlew test -Dspring.profiles.active=test
```

테스트 시나리오:

**지갑 동시성 (`PaymentConcurrencyTest`)**
1. 잔액 100,000원 / 10 스레드 / 건당 10,000원 → 모두 성공, 잔액 0원
2. 잔액 50,000원 / 10 스레드 / 건당 10,000원 → 5건 성공, 잔액 음수 없음
3. 잔액 500,000원 / 50 스레드 / 건당 10,000원 → 모두 성공
4. **잔액 10,000원 / 100 스레드 / 건당 10,000원 → 1건만 성공, 99건 잔액 부족**
5. 동일 idempotencyKey 10 스레드 동시 요청 → 1건만 성공

**재고 동시성 (`ProductConcurrencyTest`)**
1. **재고 1개 상품 / 50 스레드 → 1건만 성공, 재고 음수 없음**
2. 재고 10개 상품 / 30 스레드 → 10건 성공, 재고 0개

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
| ORD-001 | 404 | 주문 없음 |
| ORD-002 | 409 | 이미 결제된 주문 |
| PMT-001 | 404 | 결제 내역 없음 |
| PMT-003 | 409 | 중복 idempotencyKey |
| PMT-004 | 500 | 외부 결제 실패 |
| PMT-005 | 409 | 이미 취소된 결제 |
| PMT-007 | 422 | 허용되지 않은 상태 전이 |
| PRD-001 | 404 | 상품 없음 |
| PRD-002 | 422 | 재고 부족 |
| LCK-001 | 429 | 락 획득 실패 (트래픽 과부하) |
| CMN-001 | 400 | 입력값 오류 |
