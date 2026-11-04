// 온보딩(랜딩) 맨 아래 비회원 설문 섹션 (담당: 영준)
// 버튼을 누르면 App에서 관리하는 비회원 V1 설문을 연다.
// 랜딩 페이지의 클래스(landing-section, landing-reveal, page-eyebrow, btn)는 가져다 쓰기만 하고, 이 섹션 전용 스타일은 guestSurvey.css에 둔다.
import "./guestSurvey.css";

export default function GuestSurveySection({ onSurvey }) {
  return (
    <section className="landing-section rec-guest-section" aria-labelledby="rec-guest-title">
      <div className="landing-reveal">
        <p className="page-eyebrow">로그인 없이 체험</p>
        <h2 id="rec-guest-title">어떤 영양제가 필요할지 궁금하다면</h2>
        <p className="rec-guest-desc">
          로그인 없이 몇 가지 질문에 답하고 필요한 영양 성분을 알아보세요.
        </p>
        <button
          type="button"
          className="btn btn-primary btn-lg rec-guest-start"
          onClick={onSurvey}
        >
          설문 시작하기
        </button>
      </div>
    </section>
  );
}
