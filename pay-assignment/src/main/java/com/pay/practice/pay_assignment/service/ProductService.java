package com.pay.practice.pay_assignment.service;

import com.pay.practice.pay_assignment.common.error.ErrorCode;
import com.pay.practice.pay_assignment.common.error.exception.PaymentException;
import com.pay.practice.pay_assignment.domain.Product;
import com.pay.practice.pay_assignment.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 재고 차감
     *
     * @Transactional 전파 전략: REQUIRED (기본값)
     * → PaymentService.pay() 내부에서 호출되면 동일 트랜잭션에 참여
     * → 외부 결제 API 실패 시 wallet 차감과 함께 재고 차감도 자동 롤백
     *
     * 비관적 락(findByIdWithLock)을 사용하는 이유:
     * → 재고 1개 상품에 동시 100건 요청 시 음수 재고를 방지
     * → DB 레벨에서 SELECT FOR UPDATE로 직렬화, 나머지 요청은 대기 후 재고 부족 예외
     */
    @Transactional
    public void decrease(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PRODUCT_NOT_FOUND));
        product.decrease(quantity);  // 재고 부족 시 도메인에서 PaymentException 발생
    }

    /**
     * 재고 복구 - 결제 취소 시 호출
     *
     * 취소 트랜잭션(PaymentService.cancel()) 안에서 동일 트랜잭션으로 실행
     * → 취소 처리 중 예외 발생 시 재고 복구도 함께 롤백되어 일관성 유지
     */
    @Transactional
    public void increase(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PRODUCT_NOT_FOUND));
        product.increase(quantity);
    }
}
