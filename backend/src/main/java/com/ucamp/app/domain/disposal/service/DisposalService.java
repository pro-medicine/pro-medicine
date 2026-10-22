
package com.ucamp.app.domain.disposal.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DisposalService {

    private final RestClient kakaoRestClient;

    public DisposalService(@Value("${kakao.rest-api-key}") String kakaoRestApiKey) {
        this.kakaoRestClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoRestApiKey)
                .build();
    }

    public JsonNode searchPlaces(String query) {
        return kakaoRestClient.get()
                .uri("/v2/local/search/keyword.json?query={query}", query)
                .retrieve()
                .body(JsonNode.class);
    }
}
