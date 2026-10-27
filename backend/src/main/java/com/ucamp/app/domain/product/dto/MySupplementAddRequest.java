package com.ucamp.app.domain.product.dto;

// 영양제 등록 요청 본문: {"catalogProductId": 4}
public record MySupplementAddRequest(
        Long catalogProductId
) {
}
