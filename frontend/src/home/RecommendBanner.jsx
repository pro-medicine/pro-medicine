// 맞춤 영양 추천 배너 — 클릭하면 추천 설문 모달이 열린다
export default function RecommendBanner({ onClick }) {
    return (
        <button type="button" className="home-banner" onClick={onClick}>
            <span className="home-sparkle" aria-hidden="true">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m12 3-1.2 3.2L7.5 7.5l3.3 1.3L12 12l1.2-3.2 3.3-1.3-3.3-1.3L12 3Z" />
                    <path d="m5 14-.8 2.2L2 17l2.2.8L5 20l.8-2.2L8 17l-2.2-.8L5 14Z" />
                </svg>
            </span>

            <span className="home-banner-text">
                <span className="home-banner-title">내 몸에 필요한 영양소가 궁금한가요?</span>
                <span className="home-banner-sub">간단한 건강 체크로 필요한 성분을 알려드려요.</span>
            </span>

            <span className="home-banner-button">맞춤 추천 받기</span>
        </button>
    );
}
