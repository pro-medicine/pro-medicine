package com.ucamp.app.domain.chat.parse;

import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * drugs 테이블(약물 마스터, 별칭 없음)을 대화 속 약 매칭에 쓰는 형태로 읽는다 (설계 4-3). 값은 하드코딩하지 않는다.
 * 계열(drug_class) 하나에 "이름 모름" 행과 이름 아는 행이 같이 있을 수 있다 (예: ANTIPLATELET = D-013, D-U09)
 */
@Component
@RequiredArgsConstructor
public class DrugCatalog {
    /** 약 이름을 모르는 경우 */
    public static final String UNKNOWN = "UNKNOWN";
    static final String UNKNOWN_NAME_MARK = "(이름 모름)";

    private final DrugRepository drugRepository;

    public record Entry(String drugId, String name, String drugClass) {
        boolean nameUnknown() {
            return name != null && name.contains(UNKNOWN_NAME_MARK);
        }
    }

    @Transactional(readOnly = true)
    public List<Entry> all() {
        return drugRepository.findAll().stream()
                .map(d -> new Entry(d.getDrugId(), d.getName(), d.getDrugClass()))
                .sorted(Comparator.comparing(Entry::drugId))
                .toList();
    }

    /** 프롬프트·스키마용: 계열 코드 → 그 계열의 한글 이름들 (계열 순서는 drug_id 순) */
    public Map<String, List<String>> classes(List<Entry> entries) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            out.computeIfAbsent(e.drugClass(), k -> new java.util.ArrayList<>()).add(e.name());
        }
        return out;
    }

    /**
     * 검색 문장(search_query)에 넣으면 안 되는 말: 약 이름 전체 + "이름 모름" 행의 계열 한글 이름 ("항응고제 (이름 모름)" → "항응고제").
     * 약 정보는 medications로만 넘기고 검색은 증상·대상으로만 한다
     */
    public List<String> searchQueryBannedTerms(List<Entry> entries) {
        return entries.stream()
                .map(e -> e.name().replace(UNKNOWN_NAME_MARK, "").strip())
                .filter(n -> n.length() >= 2)
                .distinct()
                .toList();
    }

    /** ① 이름 일치 (공백 제거 후 비교) */
    public Optional<Entry> byName(List<Entry> entries, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = compact(name);
        return entries.stream().filter(e -> compact(e.name()).equals(key)).findFirst();
    }

    /** ② 계열: "이름 모름" 행 우선, 없으면 그 계열의 첫 행 */
    public Optional<Entry> byClass(List<Entry> entries, String drugClass) {
        if (drugClass == null || drugClass.isBlank() || UNKNOWN.equals(drugClass)) {
            return Optional.empty();
        }
        List<Entry> same = entries.stream().filter(e -> drugClass.equals(e.drugClass())).toList();
        return same.stream().filter(Entry::nameUnknown).findFirst().or(() -> same.stream().findFirst());
    }

    static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
