package com.ucamp.app.domain.review.repository;

import com.ucamp.app.domain.review.dto.ReviewProductResponse;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * catalog_products의 참고 가격 컬럼 (STEP10). 리뷰 추천(V3)과 대화형 추천이 같이 쓴다.
 * 테이블 별칭이 있으면 SELECT에 별칭을 붙여 쓴다 (예: "p.")
 */
public final class PriceColumns {
    private PriceColumns() {
    }

    public static String select(String alias) {
        return String.join(", ", alias + "price", alias + "price_package", alias + "price_source",
                alias + "price_url", alias + "price_checked_at");
    }

    /** 가격이 없으면 null. 출처 주소는 https로 시작할 때만 내보낸다 (화면에서 링크로 쓰므로) */
    public static ReviewProductResponse.Price read(ResultSet rs) throws SQLException {
        Integer amount = rs.getObject("price", Integer.class);
        if (amount == null) {
            return null;
        }
        String url = rs.getString("price_url");
        Date checked = rs.getDate("price_checked_at");
        return new ReviewProductResponse.Price(amount, rs.getString("price_package"), rs.getString("price_source"),
                url != null && url.startsWith("https://") ? url : null, checked == null ? null : checked.toLocalDate());
    }
}
