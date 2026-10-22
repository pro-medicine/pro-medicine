// 맞춤 영양 추천 배너 — 클릭하면 추천 설문 모달이 열린다
export default function RecommendBanner({ onClick }) {
    return (
        <button type="button" className="home-banner" onClick={onClick}>
            <span className="home-sparkle">✦</span>

            <span className="home-banner-text">
        <span className="home-banner-eyebrow">맞춤 영양 추천</span>
        <span className="home-banner-title">내 몸에 필요한 영양소가 궁금한가요?</span>
        <span className="home-banner-sub">간단한 건강 체크로 알아보세요</span>
      </span>

            <span className="home-arrow">›</span>
        </button>
    );
}