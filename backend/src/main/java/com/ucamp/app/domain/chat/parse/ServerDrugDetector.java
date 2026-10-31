package com.ucamp.app.domain.chat.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 약 이름 탐지 (V5 보안 4-5(4)). 사용자 입력에서 DrugCatalog 이름을 직접 찾는다.
 * LLM이 놓친 약 이름을 보완하여 안전 필터에 반영한다.
 * 약 Entry로 되돌릴 수 있는 이름만 사용 (약 계열·질환 이름 제외).
 */
public final class ServerDrugDetector {
    private ServerDrugDetector() {
    }

    /**
     * 사용자 입력에서 약 이름을 탐지한다. 2자 이상, 괄호 부분 제외, 공백 제거 후 부분 일치.
     *
     * @param text  사용자 입력 원문
     * @param drugs DrugCatalog.all() 결과
     * @return 탐지된 약 Entry 목록 (중복 제거)
     */
    public static List<DrugCatalog.Entry> detect(String text, List<DrugCatalog.Entry> drugs) {
        if (text == null || text.isBlank() || drugs == null || drugs.isEmpty()) {
            return List.of();
        }
        String compactInput = DrugCatalog.compact(text);
        List<DrugCatalog.Entry> found = new ArrayList<>();

        for (DrugCatalog.Entry entry : drugs) {
            // "(이름 모름)" 행은 건너뜀 — 약 Entry로 되돌릴 수 있는 이름만
            if (entry.nameUnknown()) {
                continue;
            }
            // 괄호 부분 제외한 이름
            String cleanName = entry.name().replaceAll("\\(.*?\\)", "").strip();
            if (cleanName.length() < 2) {
                continue;
            }
            String compactName = DrugCatalog.compact(cleanName);
            if (compactInput.contains(compactName)) {
                // 이미 같은 drugId가 있으면 건너뜀
                if (found.stream().noneMatch(e -> e.drugId().equals(entry.drugId()))) {
                    found.add(entry);
                }
            }
        }
        return List.copyOf(found);
    }
}
