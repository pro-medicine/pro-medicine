// 추천 성분 카드 1장 (담당: 영준) — ResultStep(설문·맞춤 질문 결과)과 V4 대화창 결과가 같은 카드를 쓴다
// 카드 모양·문구는 ResultStep에 있던 그대로. 제품 목록은 children으로 받는다 (V3 API 조회 / 대화창 응답에 들어 있는 제품)
import "./recommendation.css";

// 숫자 표시 (100.00 → 100, 2.5 → 2.5)
function formatNumber(value) {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return n.toLocaleString("ko-KR", { maximumFractionDigits: 2 });
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
  return (
    <li className="rec-card">
      <div className="rec-card-head">
        <strong>{name}</strong>
        <span className="rec-dose">
          {item.amount != null
            ? `하루 ${formatNumber(item.amount)}${u} 추가`
            : item.doseNote || "용량 정보 없음"}
        </span>
      </div>
      {item.amount != null && item.doseNote && (
        <small className="rec-hint">{item.doseNote}</small>
      )}
      <small className="rec-intake">
        {item.intakeStatus === "KNOWN"
          ? `현재 섭취량 ${formatNumber(item.currentIntake ?? 0)}${u} 반영`
          : "현재 섭취량 미반영"}
      </small>
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
