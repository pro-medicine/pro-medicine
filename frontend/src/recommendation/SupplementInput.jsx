// 먹고 있는 영양제 성분 입력 (담당: 영준) — 성분 + 하루 섭취량, 여러 개 추가·삭제

/**
 * props (SurveyStep에서 전달)
 * - value: [{ ingredient, amount, unit }]
 * - ingredients: 성분 코드 → { name, unit }
 * - onChange(next): 목록 변경
 */
export default function SupplementInput({ value, ingredients, onChange }) {
  const options = Object.values(ingredients);

  // 성분을 고르면 단위는 성분 목록의 단위로 고정
  const changeRow = (index, patch) => {
    onChange(value.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const addRow = () => onChange([...value, { ingredient: "", amount: "", unit: "" }]);
  const removeRow = (index) => onChange(value.filter((_, i) => i !== index));

  if (options.length === 0) {
    return <p className="rec-hint">성분 목록을 불러오지 못했어요. 이 문항은 건너뛰어 주세요.</p>;
  }

  return (
    <div className="rec-supplements">
      {value.map((row, index) => (
        <div className="rec-supplement-row" key={index}>
          <select
            aria-label="성분"
            value={row.ingredient}
            onChange={(e) =>
              changeRow(index, {
                ingredient: e.target.value,
                unit: ingredients[e.target.value]?.unit ?? "",
              })
            }
          >
            <option value="">성분 선택</option>
            {options.map((item) => (
              <option key={item.code} value={item.code}>
                {item.name}
              </option>
            ))}
          </select>
          <input
            type="number"
            min="0"
            step="any"
            inputMode="decimal"
            aria-label="하루 섭취량"
            placeholder="하루 양"
            value={row.amount}
            onChange={(e) => changeRow(index, { amount: e.target.value })}
          />
          <span className="rec-unit">{row.unit || ingredients[row.ingredient]?.unit || "-"}</span>
          <button type="button" className="rec-row-remove" aria-label="삭제" onClick={() => removeRow(index)}>
            ×
          </button>
        </div>
      ))}

      <button type="button" className="rec-add" onClick={addRow}>
        + 성분 추가
      </button>
      <small className="rec-hint">
        영양제 라벨의 1일 섭취량을 적어 주세요. 먹는 영양제가 없으면 그대로 다음으로 넘어가세요.
      </small>
    </div>
  );
}
