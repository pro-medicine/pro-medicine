package com.ucamp.app.domain.disposal.service;

import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class DisposalService {

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

    public JsonNode searchNearby(double latitude, double longitude, int radius) {
        try {
            return kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", "PM9")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radius)
                            .queryParam("sort", "distance")
                            .queryParam("size", 15)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.error("카카오 주변 약국 검색 API 호출 실패", ex);
            throw new BusinessException(ErrorCode.DISPOSAL_KAKAO_API_ERROR);
        }
    }
}
