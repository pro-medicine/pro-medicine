package com.ucamp.app.domain.disposal.service;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
@Slf4j
public class DisposalService {

    // 카카오 Local API는 페이지당 최대 15개, 페이지 번호 최대 45를 지원한다.
    private static final int KAKAO_PAGE_SIZE = 15;
    private static final int KAKAO_MAX_PAGES = 45;

    private final RestClient kakaoRestClient;

    public DisposalService(@Value("${kakao.rest-api-key}") String kakaoRestApiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.kakaoRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoRestApiKey)
                .build();
    }

    // 기존 키워드 약국 검색 API 호환성 유지
    public JsonNode searchPlaces(String query) {
        try {
            return kakaoRestClient.get()
                    .uri("/v2/local/search/keyword.json?query={query}&category_group_code=PM9&size=15", query)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.error("카카오 약국 키워드 검색 API 호출 실패", ex);
            throw new BusinessException(ErrorCode.DISPOSAL_KAKAO_API_ERROR);
        }
    }

    // 지역명으로 검색했을 때 주소/행정동/역 후보를 함께 제공한다.
    // 프론트엔드는 가장 관련성 높은 후보를 자동 선택하고 나머지 후보를 사이드 목록에 보여준다.
    public JsonNode searchLocationCandidates(String query) {
        String locationQuery = query.trim().replaceFirst("\\s*약국\\s*$", "").trim();
        if (locationQuery.isBlank()) {
            throw new BusinessException(ErrorCode.DISPOSAL_INVALID_QUERY);
        }

        try {
            ArrayNode candidates = JsonNodeFactory.instance.arrayNode();
            Set<String> seen = new HashSet<>();

            // 주소로 검색 가능한 동/도로명 주소
            JsonNode addresses = kakaoRestClient.get()
                    .uri(builder -> builder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", locationQuery)
                            .queryParam("size", 10)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            appendUniqueDocuments(candidates, seen, addresses);

            // 역/주민센터/동네 장소명 검색
            JsonNode places = kakaoRestClient.get()
                    .uri(builder -> builder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", locationQuery)
                            .queryParam("size", KAKAO_PAGE_SIZE)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            appendUniqueDocuments(candidates, seen, places);

            // 예: '발산' -> '발산1동 주민센터'도 함께 후보로 노출 시도.
            // 특정 지역명을 하드코딩하지 않고, 단순한 동네 이름에만 추가 조회한다.
            if (locationQuery.matches("[가-힣]{2,5}")
                    && !locationQuery.endsWith("역")
                    && !locationQuery.endsWith("동")) {
                JsonNode dongCandidates = kakaoRestClient.get()
                        .uri(builder -> builder
                                .path("/v2/local/search/keyword.json")
                                .queryParam("query", locationQuery + "1동 주민센터")
                                .queryParam("size", 5)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
                appendUniqueDocuments(candidates, seen, dongCandidates);
            }

            ObjectNode response = JsonNodeFactory.instance.objectNode();
            response.set("documents", candidates);
            ObjectNode meta = JsonNodeFactory.instance.objectNode();
            meta.put("total_count", candidates.size());
            meta.put("is_end", true);
            response.set("meta", meta);
            return response;
        } catch (RestClientException ex) {
            log.error("카카오 지역/주소 검색 API 호출 실패, query={}", locationQuery, ex);
            throw new BusinessException(ErrorCode.DISPOSAL_KAKAO_API_ERROR);
        }
    }

    private void appendUniqueDocuments(ArrayNode target, Set<String> seen, JsonNode response) {
        if (response == null || !response.path("documents").isArray()) return;
        for (JsonNode place : response.path("documents")) {
            String key = place.path("id").asText("");
            if (key.isBlank()) {
                key = place.path("x").asText() + ":" + place.path("y").asText();
            }
            if (seen.add(key)) target.add(place);
        }
    }

    // GPS와 지역명 검색이 함께 사용하는 좌표 기준 약국 검색.
    // 첫 페이지 15개로 끝내지 않고 Kakao meta.is_end까지 페이지를 조회한다.
    public JsonNode searchNearby(double latitude, double longitude, int radius) {
        try {
            ArrayNode pharmacies = JsonNodeFactory.instance.arrayNode();
            Set<String> seenIds = new HashSet<>();
            int totalCount = 0;
            int fetchedPages = 0;
            boolean isEnd = false;

            for (int page = 1; page <= KAKAO_MAX_PAGES; page++) {
                final int currentPage = page;
                JsonNode response = kakaoRestClient.get()
                        .uri(builder -> builder
                                .path("/v2/local/search/category.json")
                                .queryParam("category_group_code", "PM9")
                                .queryParam("x", longitude)
                                .queryParam("y", latitude)
                                .queryParam("radius", radius)
                                .queryParam("sort", "distance")
                                .queryParam("size", KAKAO_PAGE_SIZE)
                                .queryParam("page", currentPage)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null || !response.path("documents").isArray()) {
                    throw new BusinessException(ErrorCode.DISPOSAL_KAKAO_API_ERROR);
                }

                JsonNode documents = response.path("documents");
                totalCount = response.path("meta").path("total_count").asInt(totalCount);
                fetchedPages++;

                for (JsonNode place : documents) {
                    String id = place.path("id").asText("");
                    if (id.isBlank()) id = place.path("x").asText() + ":" + place.path("y").asText();
                    if (seenIds.add(id)) pharmacies.add(place);
                }

                isEnd = response.path("meta").path("is_end").asBoolean(true);
                if (isEnd || documents.isEmpty()) break;
            }

            ObjectNode merged = JsonNodeFactory.instance.objectNode();
            merged.set("documents", pharmacies);
            ObjectNode meta = JsonNodeFactory.instance.objectNode();
            meta.put("total_count", totalCount);
            meta.put("returned_count", pharmacies.size());
            meta.put("fetched_pages", fetchedPages);
            meta.put("is_end", isEnd);
            meta.put("truncated", !isEnd || totalCount > pharmacies.size());
            merged.set("meta", meta);
            return merged;
        } catch (RestClientException ex) {
            log.error("카카오 주변 약국 페이지 조회 실패: lat={}, lon={}, radius={}",
                    latitude, longitude, radius, ex);
            throw new BusinessException(ErrorCode.DISPOSAL_KAKAO_API_ERROR);
        }
    }
}
