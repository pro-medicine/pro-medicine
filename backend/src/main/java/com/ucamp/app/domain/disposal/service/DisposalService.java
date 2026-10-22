
package com.ucamp.app.domain.disposal.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
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
        return kakaoRestClient.get()
                .uri("/v2/local/search/keyword.json?query={query}&category_group_code=PM9&size=15", query)
                .retrieve()
                .body(JsonNode.class);
    }
}
