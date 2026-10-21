// 홈 상단: 날짜 + 인사말 + 알림(종) 버튼
const DAYS = ["일", "월", "화", "수", "목", "금", "토"];

// "2026-10-25" → "10월 25일 일요일"
function formatDate(dateStr) {
    const d = dateStr ? new Date(dateStr) : new Date();
    return `${d.getMonth() + 1}월 ${d.getDate()}일 ${DAYS[d.getDay()]}요일`;
}

export default function HomeHeader({ userName, date, hasUnread, onBellClick }) {
    return (
        <header className="home-header">
            <div>
                <p className="home-date">{formatDate(date)}</p>
                <h1 className="home-hello">안녕하세요, {userName}님</h1>
            </div>

            <button
                type="button"
                className="home-bell"
                onClick={onBellClick}
                aria-label="알림 열기"
            >
                <svg
                    width="22"
                    height="22"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
                </svg>
                {hasUnread && <span className="home-bell-dot" />}
            </button>
        </header>
    );
}