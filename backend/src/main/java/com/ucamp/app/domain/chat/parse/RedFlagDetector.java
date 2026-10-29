package com.ucamp.app.domain.chat.parse;

import java.util.List;
import java.util.Optional;

/**
 * 응급·위기 신호 서버 키워드 (설계 4-2). LLM red_flag와 둘 중 하나라도 걸리면 추천하지 않는다.
 * 키워드는 공백을 지운 원문에서 찾는다 ("숨이 안 쉬어져" = "숨이안쉬어져").
 * 영양제 상담에 흔한 표현과 겹치는 말은 넣지 않는다 ("의식" 단독 → "시선을 의식", "경련" → "눈꺼풀 경련"은 마그네슘 상담)
 */
public final class RedFlagDetector {
    static final List<String> EMERGENCY_KEYWORDS = List.of(
            "흉통", "가슴이너무아프", "가슴이찢어", "가슴을쥐어짜", "숨이안쉬어", "숨을못쉬", "숨쉬기힘들", "숨이막혀",
            "호흡곤란", "의식이없", "의식을잃", "의식이흐려", "의식저하", "기절", "쓰러졌", "발작", "피를토", "토혈", "혈변",
            "마비", "말이어눌", "한쪽이저리", "심하게출혈", "피가멈추지");
    static final List<String> CRISIS_KEYWORDS = List.of(
            "죽고싶", "자살", "자해", "죽어버리", "살기싫", "목숨을끊", "극단적선택", "사라지고싶");

    /** 응급 안내 (설계 4-2 고정 문구) */
    public static final String EMERGENCY_NOTICE =
            "지금 말씀하신 증상은 영양제보다 진료가 먼저 필요할 수 있어요. 가까운 병원이나 119에 연락하세요.";
    /**
     * 위기 안내. 문구·연락처는 아직 확정 전이라 임시 문구 (설계 §11 P6, 팀 검토 필요)
     * TODO(P6): 확정 문구·연락처로 교체
     */
    public static final String CRISIS_NOTICE =
            "많이 힘드시군요. 혼자 견디지 마시고 지금 바로 도움을 받으세요. "
                    + "자살예방 상담전화 109(24시간), 정신건강 위기상담 1577-0199, 긴급한 상황이면 119에 연락하세요.";

    private RedFlagDetector() {
    }

    /** CRISIS가 EMERGENCY보다 먼저 (둘 다면 위기 안내) */
    public static Optional<String> detect(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String compact = text.replaceAll("\\s+", "");
        if (CRISIS_KEYWORDS.stream().anyMatch(compact::contains)) {
            return Optional.of(ChatParse.CRISIS);
        }
        if (EMERGENCY_KEYWORDS.stream().anyMatch(compact::contains)) {
            return Optional.of(ChatParse.EMERGENCY);
        }
        return Optional.empty();
    }

    public static String notice(String level) {
        return ChatParse.CRISIS.equals(level) ? CRISIS_NOTICE : EMERGENCY_NOTICE;
    }
}
