package com.ucamp.app.domain.chat.parse;

import java.util.List;
import java.util.Optional;

/**
 * 대화 사전 필터 (V5 보안 4-5(2)). LLM 호출 전에 인젝션 패턴을 탐지한다.
 * RedFlagDetector와 같은 방식: 공백·특수문자 제거 후 부분 일치.
 * 매칭 시 LLM 호출 없이 차단하고 경고를 누적한다 (INJECTION 사유).
 */
public final class ChatPreFilter {
    public static final String INJECTION = "INJECTION";
    public static final String NOTICE = "증상을 문장으로 알려 주세요.";

    /** 정규화(전각→반각, 공백·특수문자 제거, 소문자) 후 매칭할 인젝션 패턴 */
    static final List<String> PATTERNS = List.of(
            // 한국어 인젝션
            "규칙을무시", "규칙무시", "지시를무시", "지시무시", "역할을바꿔", "역할바꿔",
            "프롬프트를보여", "프롬프트보여", "프롬프트출력", "시스템프롬프트",
            "시스템지시", "시스템메시지", "개발자모드", "탈옥", "탈옥모드",
            // 영어 인젝션 (소문자)
            "ignoreprevious", "ignoreinstructions", "ignoreabove", "ignorerules",
            "systemprompt", "showprompt", "revealinstructions", "printinstructions",
            "developermode", "jailbreak", "danmode", "youarenow",
            "forgetinstructions", "forgetrules", "actasnew", "newpersonality",
            "overrideinstructions", "overriderules", "disregard"
    );

    private ChatPreFilter() {
    }

    /**
     * 인젝션 패턴 탐지. 매칭하면 INJECTION 반환.
     */
    public static Optional<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String compact = normalize(text);
        if (PATTERNS.stream().anyMatch(compact::contains)) {
            return Optional.of(INJECTION);
        }
        return Optional.empty();
    }

    /** 전각→반각, 공백·특수문자 제거, 소문자 */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 전각 영숫자 → 반각
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            }
            // 한글, 영문, 숫자만 남기고 소문자
            if ((c >= '가' && c <= '힣') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            }
        }
        return sb.toString();
    }
}
