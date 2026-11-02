package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.user.entity.AuthDemographic;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.AuthDemographicRepository;
import com.ucamp.app.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.TestJwt.bearer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 버그1: 가입 시 입력한 성별·나이(auth_demographics)가 health_profiles에 없어도
 * 추천 모드 판단 시 폴백으로 사용되어 GENDER/BIRTH_YEAR가 missing에 포함되지 않아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
class AuthDemographicFallbackModeTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestMembers members;
    @Autowired AuthDemographicRepository authDemographicRepository;

    @Test
    void auth_demographics만_있고_health_profiles에_성별_출생연도가_없으면_missing에_포함되지_않는다() throws Exception {
        // health_profiles에 gender/birth_year 없이, 질환·약만 있는 회원
        User user = members.create(true, Map.of(), List.of("HYPERTENSION"), List.of("D-001"));
        // auth_demographics에 가입 성별·나이 저장
        authDemographicRepository.saveAndFlush(new AuthDemographic(user, 30, "여성"));

        mode(user)
                .andExpect(jsonPath("$.data.mode").value("V2"))
                .andExpect(jsonPath("$.data.missing.length()").value(0));
    }

    @Test
    void 선택_안_함이면_성별은_여전히_missing이고_출생연도는_빠진다() throws Exception {
        User user = members.create(true, Map.of(), List.of("HYPERTENSION"), List.of("D-001"));
        authDemographicRepository.saveAndFlush(new AuthDemographic(user, 25, "선택 안 함"));

        mode(user)
                .andExpect(jsonPath("$.data.mode").value("V1"))
                .andExpect(jsonPath("$.data.missing", hasItem("GENDER")))
                .andExpect(jsonPath("$.data.missing", not(hasItem("BIRTH_YEAR"))));
    }

    @Test
    void auth_demographics_행이_없으면_기존처럼_missing에_포함된다() throws Exception {
        // auth_demographics 없이, health_profiles에도 gender/birth_year 없음
        User user = members.create(true, Map.of(), List.of("HYPERTENSION"), List.of("D-001"));

        mode(user)
                .andExpect(jsonPath("$.data.mode").value("V1"))
                .andExpect(jsonPath("$.data.missing", hasItem("GENDER")))
                .andExpect(jsonPath("$.data.missing", hasItem("BIRTH_YEAR")));
    }

    @Test
    void health_profiles_값이_있으면_auth_demographics보다_우선한다() throws Exception {
        // health_profiles에 gender=M, birth_year=1990
        User user = members.create(true, Map.of("gender", "M", "birth_year", 1990),
                List.of("HYPERTENSION"), List.of("D-001"));
        // auth_demographics에 다른 값 (여성, 30세)
        authDemographicRepository.saveAndFlush(new AuthDemographic(user, 30, "여성"));

        mode(user)
                .andExpect(jsonPath("$.data.mode").value("V2"))
                .andExpect(jsonPath("$.data.missing.length()").value(0));
    }

    @Test
    void 비회원_흐름은_auth_demographics와_무관하다() throws Exception {
        // 비회원은 recommendation-mode API 자체가 401
        mockMvc.perform(get("/api/v1/users/me/recommendation-mode"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions mode(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/recommendation-mode")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());
    }
}
