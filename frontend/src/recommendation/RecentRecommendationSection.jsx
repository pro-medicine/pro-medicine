// 마이페이지 맨 아래 "최근에 추천받은 제품이에요" 섹션 (담당: 영준, V7)
// 마이페이지를 열 때는 API를 부르지 않는다. [추천 제품 보기]를 누르면 모달이 열리면서 불러온다
import { useRef, useState } from "react";
import RecentRecommendationModal from "./RecentRecommendationModal";
import "./recentRecommendation.css";

export default function RecentRecommendationSection() {
  const [open, setOpen] = useState(false);
  const openerRef = useRef(null);

  return (
    <section className="recent-rec-section" aria-label="최근 추천 제품">
      <small className="recent-rec-caption">최근에 추천받은 제품이에요</small>
      <button
        ref={openerRef}
        type="button"
        className="btn btn-soft btn-sm"
        onClick={() => setOpen(true)}
      >
        추천 제품 보기
      </button>
      {open && (
        <RecentRecommendationModal
          openerRef={openerRef}
          onClose={() => setOpen(false)}
        />
      )}
    </section>
  );
}
