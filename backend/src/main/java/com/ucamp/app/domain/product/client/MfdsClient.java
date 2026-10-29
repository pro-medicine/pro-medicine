package com.ucamp.app.domain.product.client;

import com.ucamp.app.domain.product.dto.MfdsProduct;
import lombok.extern.slf4j.Slf4j;                       // ★ 추가
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;             // ★ 추가

import java.util.ArrayList;
import java.util.List;

// 식약처 건강기능식품정보 API 호출 (공공데이터포털 HtfsInfoService03)
// 이름 검색 기능이 없는 API라서 페이지 단위로 받아 우리 DB에 저장해 두고, 검색은 DB에서 한다
@Slf4j                                                  // ★ 추가
@Component
public class MfdsClient {

    private static final String BASE_URL = "https://apis.data.go.kr/1471000/HtfsInfoService03";
    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;            // ★ 추가

    // ★ ObjectMapper도 받기 (프로젝트에 이미 있는 걸 Spring이 넣어 줌)
    public MfdsClient(@Value("${mfds.api-key}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    // 상세조회 한 페이지 (pageNo는 1부터)
    public List<MfdsProduct> fetchPage(int pageNo, int numOfRows) {
        // ★ 공공데이터포털은 JSON을 보내도 형식 표시가 text인 경우가 있어서, 글자로 받은 뒤 직접 JSON으로 읽는다
        JsonNode response;
        try {
            String body = restClient.get()
                    .uri("/getHtfsItem01?serviceKey={key}&pageNo={pageNo}&numOfRows={rows}&type=json",
                            apiKey, pageNo, numOfRows)
                    .retrieve()
                    .body(String.class);
            response = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            // 공통 오류 처리는 로그를 남기지 않아서, 식약처 호출 실패 원인을 여기서 남긴다
            log.error("식약처 API 호출 실패 pageNo={}, numOfRows={}", pageNo, numOfRows, e);
            throw e;
        }

        List<MfdsProduct> result = new ArrayList<>();
        if (response == null) {
            return result;
        }

        // items 모양이 [ {item: {...}}, ... ] 또는 {item: [...]} 로 올 수 있어서 둘 다 처리
        JsonNode items = response.path("body").path("items");
        if (items.isArray()) {
            for (JsonNode element : items) {
                JsonNode item = element.has("item") ? element.path("item") : element;
                result.add(toProduct(item));
            }
        } else if (items.path("item").isArray()) {
            for (JsonNode item : items.path("item")) {
                result.add(toProduct(item));
            }
        } else if (items.has("item")) {
            result.add(toProduct(items.path("item")));
        }
        return result;
    }

    // 식약처 응답 1건 → 우리가 쓰는 모양(MfdsProduct)으로 바꾸기
    private MfdsProduct toProduct(JsonNode item) {
        return new MfdsProduct(
                text(item, "STTEMNT_NO"),
                text(item, "PRDUCT"),
                text(item, "ENTRPS"),
                text(item, "MAIN_FNCTN"),
                text(item, "SRV_USE"),
                text(item, "INTAKE_HINT1"),
                text(item, "BASE_STANDARD"));
    }

    // 값 꺼내기: 앞뒤 공백과 캐리지 리턴(\r) 제거, 비어 있으면 null
    private String text(JsonNode item, String field) {
        String value = item.path(field).asString("");
        value = value.replace("\r", "").strip();
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }
}