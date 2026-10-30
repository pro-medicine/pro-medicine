// 성분 카드의 [제품 추천 보기] 펼침 영역 (담당: 영준, V3)
// 약사 유튜브 리뷰에서 그 성분 제품으로 많이 추천된 제품 중 안전 필터를 통과한 것 (최대 3개)
import { useEffect, useState } from "react";
import { getRecommendedProducts } from "../api/recommendationApi";

/**
 * props
 * - recommendationId: 추천 결과 id
 * - ingredient: 성분 코드 (MAGNESIUM 등)
 * - nameOf: 성분 코드 → 한글 이름
 * - products (선택): 이미 받은 제품 목록 (V4 대화창 트랙 1 응답). 주면 API를 부르지 않고 이 목록을 그린다
 * - notice (선택): products와 같이 보일 안내 문구
 */
export default function ProductRecommendations({ recommendationId, ingredient, nameOf, products, notice }) {
  if (products) {
    return <ProductList products={products} notice={notice} ingredient={ingredient} nameOf={nameOf} />;
  }
  return <FetchedProducts recommendationId={recommendationId} ingredient={ingredient} nameOf={nameOf} />;
}

function FetchedProducts({ recommendationId, ingredient, nameOf }) {
  const [state, setState] = useState({ loading: true, data: null, error: null });

  useEffect(() => {
    let alive = true;
    getRecommendedProducts(recommendationId, ingredient)
      .then((data) => alive && setState({ loading: false, data, error: null }))
      .catch((e) => alive && setState({ loading: false, data: null, error: e.message }));
    return () => {
      alive = false;
    };
  }, [recommendationId, ingredient]);

  if (state.loading) {
    return <p className="rec-prod-status">추천 제품을 찾고 있어요…</p>;
  }
  if (state.error) {
    return <p className="rec-prod-status">제품 추천을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>;
  }

  const { products = [], notice, message } = state.data ?? {};
  return (
    <ProductList products={products} notice={notice} message={message} ingredient={ingredient} nameOf={nameOf} />
  );
}

function ProductList({ products, notice, message, ingredient, nameOf }) {
  if (products.length === 0) {
    return <p className="rec-prod-status">{message || "지금은 추천할 제품이 없어요"}</p>;
  }

  return (
    <div className="rec-prod">
      <small className="rec-prod-caption">약사 리뷰에서 많이 추천된 {nameOf(ingredient)} 제품이에요</small>
      <ul className="rec-prod-list">
        {products.map((p) => (
          <li className="rec-prod-item" key={p.catalogProductId}>
            {p.imageUrl ? (
              <img className="rec-prod-thumb" src={p.imageUrl} alt="" />
            ) : (
              <span className="rec-prod-thumb placeholder" aria-hidden="true">
                {(p.brand || p.productName || "?").slice(0, 1)}
              </span>
            )}
            <div className="rec-prod-body">
              {p.brand && <span className="rec-prod-brand">{p.brand}</span>}
              <strong className="rec-prod-name">{p.productName}</strong>
              {p.alsoContains?.length > 0 && (
                <small className="rec-prod-also">
                  추천 성분 중 {p.alsoContains.map(nameOf).join(", ")}도 함께 들어 있어요
                </small>
              )}
              {p.cautions?.length > 0 && (
                <ul className="rec-prod-cautions">
                  {p.cautions.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
              )}
              {p.warnings?.length > 0 && (
                <ul className="rec-warnings">
                  {p.warnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              )}
            </div>
          </li>
        ))}
      </ul>
      {notice && <small className="rec-prod-notice">{notice}</small>}
    </div>
  );
}
