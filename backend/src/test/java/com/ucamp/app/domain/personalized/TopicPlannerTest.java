package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.planner.TopicPlanner;
import com.ucamp.app.domain.personalized.planner.TopicPlanner.AskedTurn;
import com.ucamp.app.domain.personalized.planner.TopicPlanner.Medication;
import com.ucamp.app.domain.personalized.planner.TopicPlanner.Next;
import com.ucamp.app.domain.personalized.planner.TopicPlanner.Profile;
import com.ucamp.app.domain.personalized.planner.TopicPlanner.SelectedOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 설계 5장: 주제 목록·순서(약물 우선), MULTI 2개 → 주제 2개, 예산(새 주제 5, 깊이 7), kind별 전환
class TopicPlannerTest {
    private final TopicPlanner planner = new TopicPlanner();

    // 5-5 예시: 46세 남성, 흡연 반 갑, 메트포르민, 고혈압
    private static final Profile A1 = new Profile(List.of(new Medication("METFORMIN", "메트포르민")),
            Set.of("METFORMIN"), List.of("HYPERTENSION"), "HALF_PACK", "LOW", "LOW", "OMNI", "NORMAL");

    @Test
    void 첫_질문은_항상_불편한_점_MULTI() {
        Next next = planner.next(A1, List.of()).orElseThrow();
        assertThat(next.topic().code()).isEqualTo("CONCERN_OPEN");
        assertThat(next.topic().answerType()).isEqualTo(Topic.MULTI);
        assertThat(next.depth()).isEqualTo(1);
    }

    @Test
    void 주제_순서는_불편한_점_약물_고른_불편한_점_질환_생활습관() {
        List<AskedTurn> history = List.of(turn("CONCERN_OPEN", 1,
                sel("쉽게 피곤해요", OptionKind.CONCERN), sel("잠을 잘 못 자요", OptionKind.CONCERN)));
        assertThat(planner.plan(A1, history)).extracting(Topic::code).containsExactly(
                "CONCERN_OPEN", "MED:METFORMIN", "CONCERN:쉽게 피곤해요", "CONCERN:잠을 잘 못 자요",
                "CONDITION:HYPERTENSION", "LIFESTYLE:SMOKING");
    }

    @Test
    void 규칙이_있는_약물_계열이_먼저_온다() {
        Profile p = new Profile(List.of(new Medication("STATIN", "아토르바스타틴"), new Medication(null, "기타 약"),
                new Medication("WARFARIN", "와파린")), Set.of("WARFARIN"), List.of(), null, null, null, null, null);
        assertThat(planner.plan(p, List.of())).extracting(Topic::code)
                .containsExactly("CONCERN_OPEN", "MED:WARFARIN", "MED:STATIN", "MED:기타 약");
        assertThat(planner.plan(p, List.of()).get(3).subject()).isEqualTo("기타 약");
    }

    @Test
    void 생활습관_주제는_조건에_맞을_때만() {
        Profile p = new Profile(List.of(), Set.of(), List.of(), "NONE", "HIGH", "HIGH", "VEGETARIAN", "LOW");
        assertThat(planner.plan(p, List.of())).extracting(Topic::code).containsExactly("CONCERN_OPEN",
                "LIFESTYLE:DRINKING", "LIFESTYLE:CAFFEINE", "LIFESTYLE:DIET", "LIFESTYLE:SUN");
        Profile none = new Profile(List.of(), Set.of(), List.of(), null, "LOW", "NORMAL", "OMNI", "NORMAL");
        assertThat(planner.plan(none, List.of())).extracting(Topic::code).containsExactly("CONCERN_OPEN");
    }

    @Test
    void MULTI에서_2개_고르면_kind와_상관없이_주제_2개_NONE_UNKNOWN은_제외() {
        List<AskedTurn> two = List.of(turn("CONCERN_OPEN", 1,
                sel("눈이 피로해요", OptionKind.NORMAL), sel("쉽게 피곤해요", OptionKind.CONCERN)));
        assertThat(planner.plan(empty(), two)).extracting(Topic::code)
                .containsExactly("CONCERN_OPEN", "CONCERN:눈이 피로해요", "CONCERN:쉽게 피곤해요");

        List<AskedTurn> none = List.of(turn("CONCERN_OPEN", 1, sel("없어요", OptionKind.NONE)));
        assertThat(planner.plan(empty(), none)).extracting(Topic::code).containsExactly("CONCERN_OPEN");
        assertThat(planner.next(empty(), none)).isEmpty();
    }

    @Test
    void CONCERN이면_같은_주제_깊이_2_아니면_다음_주제() {
        List<AskedTurn> history = new ArrayList<>(List.of(
                turn("CONCERN_OPEN", 1, sel("쉽게 피곤해요", OptionKind.CONCERN))));
        history.add(turn("MED:METFORMIN", 1, sel("4년 이상", OptionKind.CONCERN)));
        assertNext(A1, history, "MED:METFORMIN", 2);

        // 최대 깊이(2)에 닿으면 CONCERN이어도 다음 주제
        history.add(turn("MED:METFORMIN", 2, sel("손발이 저려요", OptionKind.CONCERN)));
        assertNext(A1, history, "CONCERN:쉽게 피곤해요", 1);

        for (OptionKind kind : List.of(OptionKind.NORMAL, OptionKind.NONE, OptionKind.UNKNOWN)) {
            List<AskedTurn> h = List.of(turn("CONCERN_OPEN", 1, sel("쉽게 피곤해요", OptionKind.CONCERN)),
                    turn("MED:METFORMIN", 1, sel("답", kind)));
            assertNext(A1, h, "CONCERN:쉽게 피곤해요", 1);
        }
    }

    @Test
    void 질환_생활습관은_깊이_1이라_CONCERN이어도_다음_주제() {
        Profile p = new Profile(List.of(), Set.of(), List.of("DIABETES"), "ONE_PACK", null, null, null, null);
        List<AskedTurn> h = List.of(turn("CONCERN_OPEN", 1, sel("없어요", OptionKind.NONE)),
                turn("CONDITION:DIABETES", 1, sel("자주 높아요", OptionKind.CONCERN)));
        assertNext(p, h, "LIFESTYLE:SMOKING", 1);
    }

    @Test
    void 설계_5_5_예시_약물_우선이면_5문항_뒤_새_주제는_시작하지_않는다() {
        List<AskedTurn> h = new ArrayList<>();
        h.add(turn("CONCERN_OPEN", 1, sel("쉽게 피곤해요", OptionKind.CONCERN), sel("잠을 잘 못 자요", OptionKind.CONCERN)));
        assertNext(A1, h, "MED:METFORMIN", 1);
        h.add(turn("MED:METFORMIN", 1, sel("4년 이상", OptionKind.CONCERN)));
        assertNext(A1, h, "MED:METFORMIN", 2);
        h.add(turn("MED:METFORMIN", 2, sel("없어요", OptionKind.NONE)));
        assertNext(A1, h, "CONCERN:쉽게 피곤해요", 1);
        h.add(turn("CONCERN:쉽게 피곤해요", 1, sel("하루 종일 피곤해요", OptionKind.CONCERN)));
        assertNext(A1, h, "CONCERN:쉽게 피곤해요", 2);
        h.add(turn("CONCERN:쉽게 피곤해요", 2, sel("없어요", OptionKind.NONE)));
        assertThat(planner.next(A1, h)).isEmpty();
    }

    @Test
    void 깊이_질문은_asked_7_전까지만_허용된다() {
        // 주제 5개를 깊이 1로 끝낸 뒤(asked = 5) CONCERN → depth 2 허용 (6번째), 그다음은 새 주제 불가
        Profile p = new Profile(List.of(new Medication("A", "a"), new Medication("B", "b"), new Medication("C", "c"),
                new Medication("D", "d")), Set.of(), List.of(), null, null, null, null, null);
        List<AskedTurn> h = new ArrayList<>();
        h.add(turn("CONCERN_OPEN", 1, sel("없어요", OptionKind.NONE)));
        h.add(turn("MED:A", 1, sel("x", OptionKind.NORMAL)));
        h.add(turn("MED:B", 1, sel("x", OptionKind.NORMAL)));
        h.add(turn("MED:C", 1, sel("x", OptionKind.NORMAL)));
        h.add(turn("MED:D", 1, sel("x", OptionKind.CONCERN)));
        assertNext(p, h, "MED:D", 2);
        h.add(turn("MED:D", 2, sel("x", OptionKind.CONCERN)));
        assertThat(planner.next(p, h)).isEmpty();
    }

    @Test
    void 질문은_최대_7개() {
        Profile p = new Profile(List.of(new Medication("A", "a"), new Medication("B", "b"), new Medication("C", "c")),
                Set.of(), List.of(), null, null, null, null, null);
        List<AskedTurn> h = new ArrayList<>();
        h.add(turn("CONCERN_OPEN", 1, sel("p", OptionKind.CONCERN)));
        h.add(turn("MED:A", 1, sel("x", OptionKind.CONCERN)));
        h.add(turn("MED:A", 2, sel("x", OptionKind.CONCERN)));
        h.add(turn("MED:B", 1, sel("x", OptionKind.CONCERN)));
        h.add(turn("MED:B", 2, sel("x", OptionKind.CONCERN)));
        assertThat(planner.next(p, h)).isEmpty(); // asked 5 → 새 주제 불가
        h.add(turn("MED:C", 1, sel("x", OptionKind.CONCERN)));
        h.add(turn("MED:C", 2, sel("x", OptionKind.CONCERN)));
        assertThat(planner.next(p, h)).isEmpty(); // asked 7
    }

    @Test
    void 남은_주제가_없으면_끝() {
        List<AskedTurn> h = List.of(turn("CONCERN_OPEN", 1, sel("눈이 피로해요", OptionKind.NORMAL)),
                turn("CONCERN:눈이 피로해요", 1, sel("아니요", OptionKind.NORMAL)));
        assertThat(planner.next(empty(), h)).isEmpty();
    }

    private void assertNext(Profile p, List<AskedTurn> h, String code, int depth) {
        Optional<Next> next = planner.next(p, h);
        assertThat(next).isPresent();
        assertThat(next.get().topic().code()).isEqualTo(code);
        assertThat(next.get().depth()).isEqualTo(depth);
    }

    private static Profile empty() {
        return new Profile(List.of(), Set.of(), List.of(), null, null, null, null, null);
    }

    private static AskedTurn turn(String topic, int depth, SelectedOption... selected) {
        return new AskedTurn(topic, depth, List.of(selected));
    }

    private static SelectedOption sel(String label, OptionKind kind) {
        return new SelectedOption(label, kind);
    }
}
