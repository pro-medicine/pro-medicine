// 온보딩(랜딩) 맨 아래 비회원 설문 섹션 (담당: 영준)
// 버튼을 누르면 기존 비회원 V1 설문(RecommendationEntry, userId 없음)을 그대로 연다. 설문·제출·결과 로직은 새로 만들지 않는다.
// 랜딩 페이지의 클래스(landing-section, landing-reveal, page-eyebrow, btn)는 가져다 쓰기만 하고, 이 섹션 전용 스타일은 guestSurvey.css에 둔다.
import { useRef, useState } from "react";
import RecommendationEntry from "./index";
import "./guestSurvey.css";

export default function GuestSurveySection() {
  const [open, setOpen] = useState(false);
  const startRef = useRef(null);

  // 닫으면 섹션의 시작 버튼으로 포커스를 돌려준다
  const close = () => {
    setOpen(false);
    requestAnimationFrame(() => startRef.current?.focus());
  };

  return (
    <section className="landing-section rec-guest-section" aria-labelledby="rec-guest-title">
      {/* 모달은 transform이 걸리는 .landing-reveal 밖에 두어야 화면 고정 위치가 틀어지지 않는다 */}
      <div className="landing-reveal">
        <p className="page-eyebrow">로그인 없이 체험</p>
        <h2 id="rec-guest-title">어떤 영양제가 필요할지 궁금하다면</h2>
        <p className="rec-guest-desc">
          로그인 없이 몇 가지 질문에 답하고 필요한 영양 성분을 알아보세요.
        </p>
        <button
          ref={startRef}
          type="button"
          className="btn btn-primary btn-lg rec-guest-start"
          onClick={() => setOpen(true)}
        >
          설문 시작하기
        </button>
      </div>
      {open && <RecommendationEntry onClose={close} />}
    </section>
  );
}
