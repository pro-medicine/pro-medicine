// 복용 약 입력 (담당: 영준) — 검색해서 고르거나, 없으면 이름을 직접 입력
import { searchDrugs } from "../api/recommendationApi";
import { useEffect, useState } from "react";

const DEBOUNCE_MS = 300; // 입력이 멈춘 뒤 검색까지 기다리는 시간

// 칩에 보여줄 이름. 미리 채운 값은 drugId만 있어서 이름이 없으면 id를 보여준다
function labelOf(item) {
  return item.label || item.name || item.drugId;
}

/**
 * props (SurveyStep에서 전달)
 * - value: 고른 약 목록 [{ drugId, label } | { name }]
 * - onChange(next): 목록 변경
 */
export default function MedicationInput({ value, onChange }) {
  const [keyword, setKeyword] = useState(""); // 검색어
  const [results, setResults] = useState([]); // 검색 결과
  const [searching, setSearching] = useState(false); // 검색 중 여부
  const [error, setError] = useState(""); // 검색 실패 메시지

  // 1) 입력이 300ms 멈추면 검색
  useEffect(() => {
    const trimmed = keyword.trim();
    if (!trimmed) return undefined; // 검색어가 없으면 결과 목록 자체를 그리지 않는다
    let cancelled = false;
    const timer = setTimeout(async () => {
      setSearching(true);
      setError("");
      try {
        const data = await searchDrugs(trimmed);
        if (!cancelled) setResults(data ?? []);
      } catch (e) {
        if (!cancelled) setError(e.message);
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [keyword]);

  // 2) 검색 결과에서 고르기 (같은 약은 한 번만)
  const addDrug = (drug) => {
    if (!value.some((item) => item.drugId === drug.drugId)) {
      onChange([...value, { drugId: drug.drugId, label: drug.name }]);
    }
    setKeyword("");
  };

  // 3) 검색 결과에 없으면 이름 그대로 추가
  const addName = () => {
    const name = keyword.trim();
    if (!name) return;
    if (!value.some((item) => item.name === name)) onChange([...value, { name }]);
    setKeyword("");
  };

  const remove = (target) => onChange(value.filter((item) => item !== target));

  return (
    <div className="rec-medication">
      <input
        type="search"
        aria-label="복용 중인 약 검색"
        placeholder="약 이름을 검색해 주세요"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && e.preventDefault()}
      />
      <small className="rec-hint">약 이름을 모르면 '혈압약'처럼 종류로 검색해 보세요.</small>

      {keyword.trim() && (
        <ul className="rec-search-results">
          {searching && <li className="rec-search-empty">검색 중...</li>}
          {error && <li className="rec-search-empty">{error}</li>}
          {!searching &&
            results.map((drug) => (
              <li key={drug.drugId}>
                <button type="button" onClick={() => addDrug(drug)}>
                  {drug.name}
                </button>
              </li>
            ))}
          <li>
            <button type="button" className="rec-direct" onClick={addName}>
              "{keyword.trim()}" 직접 입력
            </button>
          </li>
        </ul>
      )}

      {value.length > 0 ? (
        <div className="rec-chips">
          {value.map((item) => (
            <span className="rec-chip" key={item.drugId ?? `name-${item.name}`}>
              {labelOf(item)}
              <button type="button" aria-label={`${labelOf(item)} 삭제`} onClick={() => remove(item)}>
                ×
              </button>
            </span>
          ))}
        </div>
      ) : (
        <small className="rec-hint">복용 중인 약이 없으면 그대로 다음으로 넘어가세요.</small>
      )}
    </div>
  );
}
