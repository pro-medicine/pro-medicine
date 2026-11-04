// 추천 기록 카드 하나 (담당: 영준, V7.1)
// 날짜·경로 / 불편했던 점 / 추천 성분. [제품 보기]를 누르면 부모가 상세를 불러오고, 성분 카드 + 제품을 펼친다
// 카드·제품 목록은 설문 결과·대화창과 같은 IngredientCard / ProductRecommendations를 그대로 쓴다
import { useState } from "react";
import IngredientCard from "./IngredientCard";
import ProductRecommendations from "./ProductRecommendations";

const SOURCE_LABEL = {
  V1_SURVEY: "설문 추천",
  V2_PERSONALIZED: "맞춤 질문 추천",
  V4_CHAT: "대화창 추천",
};

// "2026-11-03T17:01:05" → "11월 3일 17:01"
function formatDateTime(value) {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${hh}:${mm}`;
}

function Concern({ concern }) {
  const type = concern?.type;
  if (type === "LABELS" && concern.labels?.length > 0) {
    return (
      <ul className="recent-rec-chips" aria-label="불편했던 점">
        {concern.labels.map((label) => (
          <li key={label} className="recent-rec-chip">
            {label}
          </li>
        ))}
      </ul>
    );
  }
  if (type === "TEXT") {
    return (
      <div className="recent-rec-quote">
        {/* 본인이 쓴 문장 그대로 (React 기본 이스케이프) */}
        <p className="recent-rec-clamp">“{concern.text}”</p>
        {concern.answer && <p className="recent-rec-clamp recent-rec-answer">되묻기 답: {concern.answer}</p>}
      </div>
    );
  }
  if (type === "EXPIRED") {
    return <p className="recent-rec-muted">90일이 지나 삭제됐어요</p>;
  }
  return <p className="recent-rec-muted">{concern?.text || "기록된 불편한 점이 없어요"}</p>;
}

/**
 * props
 * - item: 기록 한 줄 (source, recommendedAt, recommendationId, chatResultId, concern, ingredients)
 * - nameOf: 성분 코드 → 한글 이름
 * - unitOf: 성분 코드 → 단위
 * - detail: 불러온 상세 { loading, data, error } (아직 안 불렀으면 undefined)
 * - open: 제품을 펼쳤는지
 * - onToggle: [제품 보기] / [접기]
 */
export default function RecommendationHistoryCard({ item, nameOf, unitOf, detail, open, onToggle }) {
  const [openCards, setOpenCards] = useState(() => new Set()); // 제품 목록을 펼친 성분 (처음엔 모두 접힘)
  const titleId = `recent-rec-item-${item.chatResultId != null ? `c${item.chatResultId}` : item.recommendationId}`;
  const meta = [formatDateTime(item.recommendedAt), SOURCE_LABEL[item.source]].filter(Boolean).join(" · ");
  const cards = [...(detail?.data?.ingredients ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const toggleCard = (code) =>
    setOpenCards((cur) => {
      const next = new Set(cur);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });

  return (
    <li className="recent-rec-item" aria-labelledby={titleId}>
      <strong id={titleId} className="recent-rec-item-meta">
        {meta}
      </strong>

      <div className="recent-rec-row">
        <span className="recent-rec-label">불편했던 점</span>
        <Concern concern={item.concern} />
      </div>

      <div className="recent-rec-row">
        <span className="recent-rec-label">추천 성분</span>
        {item.ingredients?.length > 0 ? (
          <ul className="recent-rec-chips" aria-label="추천 성분">
            {item.ingredients.map((code) => (
              <li key={code} className="recent-rec-chip recent-rec-chip-ingredient">
                {nameOf(code)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="recent-rec-muted">보여줄 성분이 없어요</p>
        )}
      </div>

      <button
        type="button"
        className="btn btn-soft btn-sm recent-rec-toggle"
        aria-expanded={open}
        onClick={onToggle}
      >
        {open ? "제품 접기" : "제품 보기"}
      </button>

      {open && (
        <div className="recent-rec-detail">
          {detail?.loading && (
            <div className="rec-loading">
              <span className="rec-spinner" aria-hidden="true" />
              <strong>제품을 불러오고 있어요</strong>
            </div>
          )}
          {detail?.error && (
            <p className="recent-rec-muted" role="alert">
              제품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요
            </p>
          )}
          {detail?.data && (
            <>
              <small className="recent-rec-snapshot">
                {detail.data.snapshot ? "추천받을 때의 제품이에요" : "지금 기준 추천 제품이에요"}
              </small>
              {cards.length === 0 ? (
                <p className="recent-rec-muted">이 추천에는 보여줄 성분이 없어요.</p>
              ) : (
                <ul className="rec-cards">
                  {cards.map((card) => {
                    const products = card.products ?? [];
                    return (
                      <IngredientCard
                        key={card.ingredient}
                        item={card}
                        name={nameOf(card.ingredient)}
                        unit={unitOf(card.ingredient)}
                        showProducts={products.length > 0}
                        productsOpen={openCards.has(card.ingredient)}
                        onToggleProducts={() => toggleCard(card.ingredient)}
                      >
                        {products.length > 0 && (
                          <ProductRecommendations
                            ingredient={card.ingredient}
                            nameOf={nameOf}
                            products={products}
                            notice={detail.data.notice}
                          />
                        )}
                      </IngredientCard>
                    );
                  })}
                </ul>
              )}
            </>
          )}
        </div>
      )}
    </li>
  );
}
