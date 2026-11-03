// 마이페이지 "최근 추천 제품 보기" 모달 (담당: 영준, V7)
// 가장 최근 추천 1건의 성분 카드 + 성분별 제품. 저장된 결과만 읽는다 (새 추천을 만들지 않음)
// 카드·제품 목록은 설문 결과·대화창과 같은 IngredientCard / ProductRecommendations를 그대로 쓴다
import { useEffect, useState } from "react";
import { getIngredients, getRecentRecommendation } from "../api/recommendationApi";
import { useModalFocus } from "../common/useModalFocus";
import IngredientCard from "./IngredientCard";
import ProductRecommendations from "./ProductRecommendations";
import "./recentRecommendation.css";

const SOURCE_LABEL = {
  V1_SURVEY: "설문 추천",
  V2_PERSONALIZED: "맞춤 질문 추천",
  V4_CHAT: "대화창 추천",
};

// "2026-11-01T21:03:11" → "11월 1일"
function formatDate(value) {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/**
 * props
 * - onClose: 모달 닫기
 * - openerRef: 모달을 연 버튼 (닫으면 포커스를 돌려준다)
 */
export default function RecentRecommendationModal({ onClose, openerRef }) {
  const panelRef = useModalFocus(onClose, openerRef);
  const [state, setState] = useState({ loading: true, data: null, error: false });
  const [ingredients, setIngredients] = useState({}); // 성분 코드 → { name, unit }
  const [closedCards, setClosedCards] = useState(() => new Set()); // 제품 목록을 접은 성분

  useEffect(() => {
    let alive = true;
    // 성분 이름을 못 받아도 코드로 표시하고 진행
    Promise.all([getRecentRecommendation(), getIngredients().catch(() => [])])
      .then(([data, list]) => {
        if (!alive) return;
        setIngredients(Object.fromEntries((list ?? []).map((i) => [i.code, i])));
        setState({ loading: false, data, error: false });
      })
      .catch(() => alive && setState({ loading: false, data: null, error: true }));
    return () => {
      alive = false;
    };
  }, []);

  const nameOf = (code) => ingredients[code]?.name ?? code;
  const toggle = (code) =>
    setClosedCards((cur) => {
      const next = new Set(cur);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });

  const { loading, data, error } = state;
  const cards = [...(data?.ingredients ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const meta = data?.exists
    ? [formatDate(data.recommendedAt), SOURCE_LABEL[data.source]].filter(Boolean).join(" · ")
    : "";

  return (
    <div className="rec-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        ref={panelRef}
        className="rec-modal recent-rec-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="recent-rec-title"
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="rec-header">
          <div>
            <h2 id="recent-rec-title">최근에 추천받은 제품이에요</h2>
            {meta && <small className="recent-rec-meta">{meta}</small>}
          </div>
          <button type="button" className="rec-close" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>

        {loading && (
          <div className="rec-loading">
            <span className="rec-spinner" aria-hidden="true" />
            <strong>추천 기록을 불러오고 있어요</strong>
          </div>
        )}

        {!loading && error && (
          <div className="rec-error">
            <p>추천 기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요</p>
          </div>
        )}

        {!loading && !error && !data?.exists && (
          <div className="rec-empty">
            아직 추천받은 기록이 없어요.
            <br />
            홈의 맞춤 영양 추천이나 대화창에서 추천을 받아 보세요.
          </div>
        )}

        {!loading && !error && data?.exists && cards.length === 0 && (
          <div className="rec-empty">이 추천에는 보여줄 성분이 없어요.</div>
        )}

        {!loading && !error && data?.exists && cards.length > 0 && (
          <ul className="rec-cards">
            {cards.map((card) => {
              const products = card.products ?? [];
              return (
                <IngredientCard
                  key={card.ingredient}
                  item={card}
                  name={nameOf(card.ingredient)}
                  unit={ingredients[card.ingredient]?.unit || ""}
                  showProducts
                  productsOpen={!closedCards.has(card.ingredient)}
                  onToggleProducts={() => toggle(card.ingredient)}
                >
                  {products.length > 0 ? (
                    <ProductRecommendations
                      ingredient={card.ingredient}
                      nameOf={nameOf}
                      products={products}
                      notice={data.notice}
                    />
                  ) : (
                    <p className="rec-prod-status">아직 추천할 제품이 없어요</p>
                  )}
                </IngredientCard>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

