package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.product.dto.MySupplementAddRequest;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.entity.UserProduct;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.domain.product.repository.UserProductRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 마이페이지 "내가 먹는 영양제" 목록
// 흐름: 유저 확인 → 복용 중인 제품 조회 → 그 제품들의 복용 일정 조회 → 제품마다 일정을 붙여 응답
// 쿼리는 제품 1번 + 일정 1번 = 총 2번 (제품마다 따로 조회하는 N+1을 피함)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MySupplementService {

    private final UserProductRepository userProductRepository;
    private final IntakeScheduleRepository intakeScheduleRepository;
    private final UserRepository userRepository;
    private final CatalogProductRepository catalogProductRepository;

    public List<MySupplementResponse> getMySupplements(Long userId) {
        // 0. 없는 유저면 404 (U001)
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 1. 복용 중인 제품 (제품 정보 포함, 등록순)
        List<UserProduct> products = userProductRepository.findActiveWithCatalogByUserId(userId);
        if (products.isEmpty()) {
            // 먹는 영양제가 없으면 빈 목록
            // 일정 조회의 IN ( ) 안이 비면 쿼리 오류가 날 수 있어서 여기서 먼저 끝낸다
            return List.of();
        }

        // 2. 그 제품들의 복용 일정을 한 번에 조회해서 제품별로 나눈다
        // 2-1. 제품 id 목록 만들기 (예: [1, 2, 3])
        List<Long> productIds = new ArrayList<>();
        for (UserProduct product : products) {
            productIds.add(product.getUserProductId());
        }
        // 2-2. schedulesByProduct: 제품 id → 그 제품의 일정 목록 (예: 1 → [아침 식후])
        Map<Long, List<MySupplementResponse.ScheduleResponse>> schedulesByProduct = new HashMap<>();
        for (IntakeSchedule schedule : intakeScheduleRepository.findEnabledByUserProductIds(productIds)) {
            Long productId = schedule.getUserProduct().getUserProductId();
            List<MySupplementResponse.ScheduleResponse> list = schedulesByProduct.get(productId);
            if (list == null) { // 이 제품의 첫 일정이면 빈 목록을 새로 만든다
                list = new ArrayList<>();
                schedulesByProduct.put(productId, list);
            }
            list.add(MySupplementResponse.ScheduleResponse.from(schedule));
        }

        // 3. 제품마다 일정을 붙여서 응답 (제품 순서는 1번의 등록순 그대로)
        List<MySupplementResponse> result = new ArrayList<>();
        for (UserProduct product : products) {
            // 일정이 없는 제품은 빈 목록
            List<MySupplementResponse.ScheduleResponse> schedules =
                    schedulesByProduct.getOrDefault(product.getUserProductId(), List.of());
            result.add(MySupplementResponse.of(product, schedules));
        }
        return result;
    }

    // 등록 흐름: 값 검사 → 유저·제품 확인 → 중복 확인 → 저장
    @Transactional
    public MySupplementResponse addMySupplement(Long userId, MySupplementAddRequest request) {
        // 1. 제품 id가 없으면 400
        if (request == null || request.catalogProductId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        // 2. 유저가 없으면 404 (U001), 제품이 없으면 404 (P001)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        CatalogProduct product = catalogProductRepository.findById(request.catalogProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        // 3. 이미 먹고 있는 제품이면 409 (P002)
        boolean alreadyAdded = userProductRepository
                .existsByUserIdAndCatalogProductCatalogProductIdAndIsActiveTrue(userId, product.getCatalogProductId());
        if (alreadyAdded) {
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_ADDED);
        }
        // 4. 저장 (검색해서 등록한 영양제, 오늘부터 복용)
        UserProduct userProduct = UserProduct.builder()
                .user(user)
                .catalogProduct(product)
                .productKind("SUPPLEMENT")
                .registeredVia("SEARCH")
                .startDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
                .isActive(true)
                .build();
        UserProduct saved = userProductRepository.save(userProduct);
        // 방금 등록해서 복용 일정은 아직 없음
        return MySupplementResponse.of(saved, List.of());
    }

    // 삭제 흐름: 등록한 영양제 찾기 → 내 것인지 확인 → 복용 중단으로 바꾸기
    @Transactional
    public void removeMySupplement(Long userId, Long userProductId) {
        UserProduct userProduct = userProductRepository.findById(userProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND));
        // 다른 사람 것이거나 이미 삭제한 것도 404 (P003)
        boolean isMine = userProduct.getUser().getId().equals(userId);
        if (!isMine || !userProduct.getIsActive()) {
            throw new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND);
        }
        // 값만 바꾸면 트랜잭션이 끝날 때 JPA가 UPDATE (save 불필요)
        userProduct.deactivate();
    }
}