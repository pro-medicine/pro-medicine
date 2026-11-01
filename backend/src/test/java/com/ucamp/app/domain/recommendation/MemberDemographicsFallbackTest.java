package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.service.MemberDemographicsFallback;
import com.ucamp.app.domain.user.entity.AuthDemographic;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.AuthDemographicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * MemberDemographicsFallback 단위 테스트.
 * auth_demographics의 가입 성별·나이를 health_profiles 코드 형식으로 변환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MemberDemographicsFallbackTest {

    @Mock
    AuthDemographicRepository authDemographicRepository;

    @InjectMocks
    MemberDemographicsFallback fallback;

    @Test
    void 여성은_F로_변환된다() {
        AuthDemographic ad = demographic(30, "여성");
        given(authDemographicRepository.findById(1L)).willReturn(Optional.of(ad));
        assertThat(fallback.resolveGenderCode(1L)).isEqualTo("F");
    }

    @Test
    void 남성은_M으로_변환된다() {
        AuthDemographic ad = demographic(25, "남성");
        given(authDemographicRepository.findById(2L)).willReturn(Optional.of(ad));
        assertThat(fallback.resolveGenderCode(2L)).isEqualTo("M");
    }

    @Test
    void 선택_안_함은_null이다() {
        AuthDemographic ad = demographic(20, "선택 안 함");
        given(authDemographicRepository.findById(3L)).willReturn(Optional.of(ad));
        assertThat(fallback.resolveGenderCode(3L)).isNull();
    }

    @Test
    void auth_demographics_행이_없으면_성별은_null이다() {
        given(authDemographicRepository.findById(99L)).willReturn(Optional.empty());
        assertThat(fallback.resolveGenderCode(99L)).isNull();
    }

    @Test
    void 출생_연도는_동의시각_연도에서_나이를_뺀다() {
        AuthDemographic ad = demographic(30, "남성");
        given(authDemographicRepository.findById(1L)).willReturn(Optional.of(ad));
        int expected = ad.getTermsAgreedAt().getYear() - 30;
        assertThat(fallback.resolveBirthYear(1L)).isEqualTo(expected);
    }

    @Test
    void 나이가_null이면_출생_연도는_null이다() {
        AuthDemographic ad = demographic(null, "여성");
        given(authDemographicRepository.findById(4L)).willReturn(Optional.of(ad));
        assertThat(fallback.resolveBirthYear(4L)).isNull();
    }

    @Test
    void auth_demographics_행이_없으면_출생_연도는_null이다() {
        given(authDemographicRepository.findById(99L)).willReturn(Optional.empty());
        assertThat(fallback.resolveBirthYear(99L)).isNull();
    }

    private static AuthDemographic demographic(Integer age, String gender) {
        User user = mock(User.class);
        return new AuthDemographic(user, age, gender);
    }
}
