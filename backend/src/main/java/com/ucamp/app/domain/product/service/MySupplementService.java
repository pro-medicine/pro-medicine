package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.product.dto.MySupplementAddRequest;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.dto.MySupplementScheduleUpdateRequest;
import com.ucamp.app.domain.product.dto.ScheduleRequest;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.entity.UserProduct;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.domain.product.repository.SupplementScheduleRepository;
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

    // 복용 일정에 쓸 수 있는 값 (intake_schedules.meal_slot, meal_relation)
    private static final List<String> MEAL_SLOTS = List.of("BREAKFAST", "LUNCH", "DINNER");
    private static final List<String> MEAL_RELATIONS = List.of("BEFORE", "AFTER", "ANY");

    private final UserProductRepository userProductRepository;
    private final SupplementScheduleRepository supplementScheduleRepository;
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
        for (IntakeSchedule schedule : supplementScheduleRepository.findEnabledByUserProductIds(productIds)) {
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

    // 등록 흐름: 값 검사 → 유저·제품 확인 → 중복 확인 → 영양제 저장 → 복용 일정 저장
    @Transactional
    public MySupplementResponse addMySupplement(Long userId, MySupplementAddRequest request) {
        // 1. 제품 id가 없거나 일정 값이 잘못되면 400 (일정은 없어도 됨)
        if (request == null || request.catalogProductId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<ScheduleRequest> scheduleRequests = request.schedules() == null ? List.of() : request.schedules();
        validateSchedules(scheduleRequests);
        // 2. 유저가 없으면 404 (U001), 제품이 없으면 404 (P001)
        User user = userRepository.findWithLockById(userId)
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
        // 5. 복용 일정 저장
        List<MySupplementResponse.ScheduleResponse> schedules = saveSchedules(saved, scheduleRequests);
        return MySupplementResponse.of(saved, schedules);
    }

    // 복용 일정 수정 흐름: 내 영양제 확인 → 값 검사 → 기존 일정 끄기 → 새 일정 저장
    @Transactional
    public MySupplementResponse updateSchedules(Long userId, Long userProductId,
                                                MySupplementScheduleUpdateRequest request) {
        UserProduct userProduct = findMyActiveProduct(userId, userProductId);
        // 1. 일정 목록이 없으면 400 ("일정 없음"은 null이 아니라 빈 배열 [])
        if (request == null || request.schedules() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        validateSchedules(request.schedules());

        // 2. 기존 일정은 지우지 않고 끈다 (복용 기록이 기존 일정을 가리키고 있어서)
        supplementScheduleRepository.disableAllByUserProductId(userProductId);
        // 3. 새 일정 저장
        List<MySupplementResponse.ScheduleResponse> schedules = saveSchedules(userProduct, request.schedules());
        return MySupplementResponse.of(userProduct, schedules);
    }

    // 삭제 흐름: 등록한 영양제 찾기 → 내 것인지 확인 → 복용 중단으로 바꾸기
    @Transactional
    public void removeMySupplement(Long userId, Long userProductId) {
        findMyActiveProduct(userId, userProductId);
        userProductRepository.deactivate(userProductId);
    }

    // 내가 등록한, 복용 중인 영양제 찾기
    // 없거나, 다른 사람 것이거나, 이미 삭제한 것은 404 (P003)
    private UserProduct findMyActiveProduct(Long userId, Long userProductId) {
        UserProduct userProduct = userProductRepository.findById(userProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND));
        boolean isMine = userProduct.getUser().getId().equals(userId);
        if (!isMine || !userProduct.getIsActive()) {
            throw new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND);
        }
        return userProduct;
    }

    // 일정 값 검사: 끼니·식전후 코드가 정해진 값인지, 알림 시각이 있는지, 같은 끼니가 두 번 없는지 (잘못되면 400)
    private void validateSchedules(List<ScheduleRequest> requests) {
        List<String> usedSlots = new ArrayList<>();
        for (ScheduleRequest request : requests) {
            if (request == null
                    || !MEAL_SLOTS.contains(request.mealSlot())
                    || !MEAL_RELATIONS.contains(request.mealRelation())
                    || request.remindAt() == null
                    || usedSlots.contains(request.mealSlot())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            usedSlots.add(request.mealSlot());
        }
    }

    // 일정 저장 후, 응답에 쓸 모양으로 돌려준다
    private List<MySupplementResponse.ScheduleResponse> saveSchedules(UserProduct userProduct,
                                                                     List<ScheduleRequest> requests) {
        List<MySupplementResponse.ScheduleResponse> result = new ArrayList<>();
        for (ScheduleRequest request : requests) {
            IntakeSchedule schedule = IntakeSchedule.builder()
                    .userProduct(userProduct)
                    .mealSlot(request.mealSlot())
                    .mealRelation(request.mealRelation())
                    .remindAt(request.remindAt())
                    .isEnabled(true)
                    .build();
            result.add(MySupplementResponse.ScheduleResponse.from(supplementScheduleRepository.save(schedule)));
        }
        return result;
    }
}