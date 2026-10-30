package com.ucamp.app.domain.product.client;

import com.ucamp.app.domain.product.dto.MfdsProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 식약처 건강기능식품정보 API 호출 (공공데이터포털 HtfsInfoService03)
// 이름 검색 기능이 없는 API라서 페이지 단위로 받아 우리 DB에 저장해 두고, 검색은 DB에서 한다
@Slf4j
@Component
public class MfdsClient {

    private static final String BASE_URL = "https://apis.data.go.kr/1471000/HtfsInfoService03";
    // 식약처 응답이 늦어도 무한정 기다리지 않도록 (연결 5초, 응답 30초)
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    // 키는 선택 설정: 없어도 서버는 켜지고, import할 때만 오류 (application.yml의 mfds.api-key)
    public MfdsClient(@Value("${mfds.api-key:}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    // 상세조회 한 페이지 (pageNo는 1부터)
    public List<MfdsProduct> fetchPage(int pageNo, int numOfRows) {
        // 키가 없으면 빈 키로 식약처를 부르지 않고 바로 알려준다
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("식약처 API 키가 없습니다. backend/.env에 MFDS_API_KEY를 설정해 주세요.");
        }

        // 공공데이터포털은 JSON을 보내도 형식 표시가 text인 경우가 있어서, 글자로 받은 뒤 직접 JSON으로 읽는다
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

        // 결과 코드가 "00"(정상)이 아니면 실패로 본다 (오류 응답을 빈 페이지로 착각하지 않게)
        // 정상인데 items가 없으면 마지막 페이지 뒤라서 빈 목록
        String resultCode = response.path("header").path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            String resultMsg = response.path("header").path("resultMsg").asString("");
            log.error("식약처 API 오류 응답 pageNo={}, resultCode={}, resultMsg={}", pageNo, resultCode, resultMsg);
            throw new IllegalStateException("식약처 API 오류 응답: " + resultCode + " " + resultMsg);
        }

        List<MfdsProduct> result = new ArrayList<>();

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