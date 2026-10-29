// 대화창 말풍선 1개 (담당: 영준) — 챗봇은 왼쪽, 사용자는 오른쪽
import { useEffect, useState } from "react";
import { getProductAvailability } from "../api/recommendationApi";
import IngredientCard from "../recommendation/IngredientCard";
import ProductRecommendations from "../recommendation/ProductRecommendations";

/**
 * props
 * - message: { from: bot | user, kind: text | chips | result | pending | alert, text, chips, result, tone }
 * - active: 마지막 말풍선이라 선택지를 누를 수 있는지
 * - ingredients: 성분 코드 → { name, unit }
 * - onChoose: 선택지 누름 (message, chip)
 */
export default function ChatMessage({ message, active, ingredients, onChoose }) {
  const mine = message.from === "user";
  return (
    <li className={`chat-row ${mine ? "mine" : "bot"}`}>
      {!mine && (
        <span className="chat-avatar" aria-hidden="true">
          약
        </span>
      )}
      <div className={`chat-bubble${message.kind === "alert" ? ` alert ${message.tone ?? ""}` : ""}${message.kind === "result" ? " wide" : ""}`}>
        {message.text && <p className="chat-text">{message.text}</p>}
        {message.kind === "pending" && (
          <span className="chat-dots" role="status" aria-label="추천을 기다리는 중">
            <i />
            <i />
            <i />
          </span>
        )}
        {message.kind === "chips" && (
          <div className="chat-chips">
            {message.chips.map((chip) => (
              <button
                type="button"
                key={chip.code ?? chip.label}
                className="chat-chip"
                disabled={!active}
                onClick={() => onChoose(message, chip)}
              >
                {chip.label}
              </button>
            ))}
          </div>
        )}
        {message.kind === "result" && <ChatResult result={message.result} ingredients={ingredients} />}
      </div>
    </li>
  );
}

// 결과 말풍선: 성분 카드 + [제품 추천 보기]. 트랙 1은 응답에 든 제품을 바로 펼치고, 트랙 2는 V3 제품 API
function ChatResult({ result, ingredients }) {
  const nameOf = (code) => ingredients[code]?.name ?? code;
  const ruleBased = result.track === "RULE_BASED";
  const cards = result.cards ?? [];
  const excluded = result.excludedItems ?? [];
  const [open, setOpen] = useState(null);
  const [availability, setAvailability] = useState({});

  useEffect(() => {
    if (!ruleBased || !result.recommendationId || cards.length === 0) return undefined;
    let alive = true;
    getProductAvailability(result.recommendationId)
      .then((data) => alive && setAvailability(data ?? {}))
      .catch(() => alive && setAvailability({}));
    return () => {
      alive = false;
    };
  }, [ruleBased, result.recommendationId, cards.length]);

  return (
    <div className="chat-result">
      {ruleBased && result.message && <p className="chat-text">{result.message}</p>}
      {result.medicationNotice && <p className="chat-note warn">{result.medicationNotice}</p>}
      {cards.length === 0 ? (
        <p className="chat-note">말씀하신 내용으로는 지금 바로 권할 성분이 없어요. 의사·약사와 상담해 주세요.</p>
      ) : (
        <ul className="rec-cards">
          {cards.map((card) => (
            <IngredientCard
              key={card.ingredient}
              item={card}
              name={nameOf(card.ingredient)}
              unit={ingredients[card.ingredient]?.unit || ""}
              showProducts={ruleBased ? Boolean(availability[card.ingredient]) : card.products?.length > 0}
              productsOpen={open === card.ingredient}
              onToggleProducts={() => setOpen((cur) => (cur === card.ingredient ? null : card.ingredient))}
            >
              {ruleBased ? (
                <ProductRecommendations
                  recommendationId={result.recommendationId}
                  ingredient={card.ingredient}
                  nameOf={nameOf}
                />
              ) : (
                <ProductRecommendations
                  ingredient={card.ingredient}
                  nameOf={nameOf}
                  products={card.products}
                  notice={result.notice}
                />
              )}
            </IngredientCard>
          ))}
        </ul>
      )}
      {excluded.length > 0 && (
        <details className="rec-excluded">
          <summary>안전을 위해 제외한 성분 ({excluded.length})</summary>
          <ul>
            {excluded.map((item) => (
              <li key={item.ingredient}>
                <strong>{nameOf(item.ingredient)}</strong>
                {(item.warnings ?? []).map((w) => (
                  <p key={w}>{w}</p>
                ))}
              </li>
            ))}
          </ul>
        </details>
      )}
      <p className="chat-footnote">이 결과는 의학적 진단이 아니에요. 복용 전 의사·약사와 상담하세요.</p>
    </div>
  );
}
