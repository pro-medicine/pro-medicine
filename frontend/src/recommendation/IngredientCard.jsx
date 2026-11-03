// 추천 성분 카드 1장 (담당: 영준) — ResultStep(설문·맞춤 질문 결과)과 V4 대화창 결과가 같은 카드를 쓴다
// 카드 모양·문구는 ResultStep에 있던 그대로. 제품 목록은 children으로 받는다 (V3 API 조회 / 대화창 응답에 들어 있는 제품)
import "./recommendation.css";

// 숫자 표시 (100.00 → 100, 2.5 → 2.5)
function formatNumber(value) {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return n.toLocaleString("ko-KR", { maximumFractionDigits: 2 });
}

// 서버 DoseCalculator의 doseNote 문구 (NOTE_INTAKE_UNKNOWN, NOTE_UNIT_MISMATCH, NOTE_NOT_NEEDED)
// 여러 개면 ", "로 이어 붙여서 온다 (예: "현재 섭취량 미반영, 상한섭취량에 맞춰 조정")
const NOTE_INTAKE_UNKNOWN = "현재 섭취량 미반영";
const NOTE_UNIT_MISMATCH = "현재 섭취량 단위가 기준 단위와 달라 미반영";
const NOTE_NOT_NEEDED = "추가 섭취 필요 없음";

// doseNote를 나눠서 다른 줄(섭취량 줄·제목 옆)에 이미 나오는 문구를 빼고 나머지만 돌려준다
function splitDoseNote(doseNote) {
  const parts = doseNote ? doseNote.split(", ").filter(Boolean) : [];
  const hint = parts
    .filter((p) => p !== NOTE_INTAKE_UNKNOWN && p !== NOTE_UNIT_MISMATCH && p !== NOTE_NOT_NEEDED)
    .join(", ");
  return { hint, unitMismatch: parts.includes(NOTE_UNIT_MISMATCH) };
}

/**
 * props
 * - item: 추천 성분 (ingredient, amount, unit, doseNote, intakeStatus, currentIntake, reason, warnings)
 * - name: 성분 이름
 * - unit: 단위 (item.unit이 없을 때 성분 목록의 단위)
 * - showProducts: [제품 추천 보기] 버튼을 보일지
 * - productsOpen: 제품 목록이 펼쳐졌는지
 * - onToggleProducts: 버튼 클릭
 * - children: 펼쳤을 때 보일 제품 목록
 */
export default function IngredientCard({
  item,
  name,
  unit = "",
  showProducts = false,
  productsOpen = false,
  onToggleProducts,
  children,
}) {
  const u = item.unit || unit;
  const hasAmount = item.amount != null;
  const { hint, unitMismatch } = splitDoseNote(item.doseNote);
  // 용량이 0이면 "하루 0mg 추가" 대신 "추가 섭취 필요 없음"
  const doseText = !hasAmount
    ? item.doseNote || "용량 정보 없음"
    : Number(item.amount) === 0
      ? NOTE_NOT_NEEDED
      : `하루 ${formatNumber(item.amount)}${u} 추가`;
  // 용량 계산을 못 했으면(기준값 미등록 등) 섭취량 줄을 그리지 않는다
  const showIntake = hasAmount || Boolean(item.intakeStatus);
  return (
    <li className="rec-card">
      <div className="rec-card-head">
        <strong>{name}</strong>
        <span className="rec-dose">{doseText}</span>
      </div>
      {hasAmount && hint && <small className="rec-hint">{hint}</small>}
      {showIntake && (
        <small className="rec-intake">
          {item.intakeStatus === "KNOWN"
            ? `현재 섭취량 ${formatNumber(item.currentIntake ?? 0)}${u} 반영`
            : unitMismatch
              ? NOTE_UNIT_MISMATCH
              : NOTE_INTAKE_UNKNOWN}
        </small>
      )}
      {item.reason && <p className="rec-reason">{item.reason}</p>}
      {item.warnings?.length > 0 && (
        <ul className="rec-warnings">
          {item.warnings.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
      )}
      {showProducts && (
        <>
          <button
            type="button"
            className={`rec-product${productsOpen ? " open" : ""}`}
            aria-expanded={productsOpen}
            onClick={onToggleProducts}
          >
            {productsOpen ? "제품 추천 접기" : "제품 추천 보기"}
          </button>
          {productsOpen && children}
        </>
      )}
    </li>
  );
}
