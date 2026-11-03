// 마이페이지 "추천 기록" 모달 (담당: 영준, V7 → V7.1)
// 최근 추천 20건 (설문·맞춤 질문·대화창, 최신순): 날짜·경로 / 불편했던 점 / 추천 성분. 저장된 결과만 읽는다 (새 추천을 만들지 않음)
// 제품은 기록마다 [제품 보기]를 누를 때 불러오고, 모달이 열려 있는 동안은 다시 부르지 않는다
import { useEffect, useState } from "react";
import {
  getIngredients,
  getRecommendationHistory,
  getRecommendationHistoryDetail,
} from "../api/recommendationApi";
import { useModalFocus } from "../common/useModalFocus";
import RecommendationHistoryCard from "./RecommendationHistoryCard";
import "./recentRecommendation.css";

// 기록 하나의 키 (대화창 트랙 1은 chatResultId, 그 밖에는 recommendationId)
const keyOf = (item) =>
  item.chatResultId != null ? `chat-${item.chatResultId}` : `rec-${item.recommendationId}`;

/**
 * props
 * - onClose: 모달 닫기
 * - openerRef: 모달을 연 버튼 (닫으면 포커스를 돌려준다)
 */
export default function RecentRecommendationModal({ onClose, openerRef }) {
  const panelRef = useModalFocus(onClose, openerRef);
  const [state, setState] = useState({ loading: true, items: [], error: false });
  const [ingredients, setIngredients] = useState({}); // 성분 코드 → { name, unit }
  const [details, setDetails] = useState({}); // 기록 키 → { loading, data, error }
  const [openKeys, setOpenKeys] = useState(() => new Set()); // 제품을 펼친 기록

  useEffect(() => {
    let alive = true;
    // 성분 이름을 못 받아도 코드로 표시하고 진행
    Promise.all([getRecommendationHistory(), getIngredients().catch(() => [])])
      .then(([data, list]) => {
        if (!alive) return;
        setIngredients(Object.fromEntries((list ?? []).map((i) => [i.code, i])));
        setState({ loading: false, items: data?.items ?? [], error: false });
      })
      .catch(() => alive && setState({ loading: false, items: [], error: true }));
    return () => {
      alive = false;
    };
  }, []);

  const nameOf = (code) => ingredients[code]?.name ?? code;
  const unitOf = (code) => ingredients[code]?.unit || "";

  const loadDetail = (item, key) => {
    setDetails((cur) => ({ ...cur, [key]: { loading: true, data: null, error: false } }));
    getRecommendationHistoryDetail(item)
      .then((data) => setDetails((cur) => ({ ...cur, [key]: { loading: false, data, error: false } })))
      .catch(() => setDetails((cur) => ({ ...cur, [key]: { loading: false, data: null, error: true } })));
  };

  const toggle = (item) => {
    const key = keyOf(item);
    const opening = !openKeys.has(key);
    setOpenKeys((cur) => {
      const next = new Set(cur);
      if (opening) next.add(key);
      else next.delete(key);
      return next;
    });
    // 처음 펼칠 때만 부른다. 오류였으면 다시 펼칠 때 다시 시도
    const detail = details[key];
    if (opening && (!detail || detail.error)) loadDetail(item, key);
  };

  const { loading, items, error } = state;

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
            <h2 id="recent-rec-title">추천 기록</h2>
            <small className="recent-rec-meta">최근 추천 20건까지 보여드려요</small>
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

        {!loading && !error && items.length === 0 && (
          <div className="rec-empty">
            아직 추천받은 기록이 없어요.
            <br />
            홈의 맞춤 영양 추천이나 대화창에서 추천을 받아 보세요.
          </div>
        )}

        {!loading && !error && items.length > 0 && (
          <ul className="recent-rec-list">
            {items.map((item) => {
              const key = keyOf(item);
              return (
                <RecommendationHistoryCard
                  key={key}
                  item={item}
                  nameOf={nameOf}
                  unitOf={unitOf}
                  detail={details[key]}
                  open={openKeys.has(key)}
                  onToggle={() => toggle(item)}
                />
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
