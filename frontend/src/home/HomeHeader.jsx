
// 홈 상단: 날짜 + 인사말 + 로그아웃 + 알림

function formatToday() {
    return new Intl.DateTimeFormat("ko-KR", {
        timeZone: "Asia/Seoul",
        month: "long",
        day: "numeric",
        weekday: "long",
    }).format(new Date());
}

export default function HomeHeader({
                                       userName,
                                       hasUnread,
                                       onBellClick,
                                       headerActions,
                                   }) {
    return (
        <header className="home-header">

            {/* 왼쪽: 날짜와 인사말 */}
            <div>
                <p className="home-date">{formatToday()}</p>

                <h1 className="home-hello">
                    안녕하세요, {userName}님
                </h1>
            </div>

            {/* 오른쪽: 로그아웃 + 알림 */}
            <div className="home-header-controls">

                {/* App.jsx에서 전달한 사용자 정보와 로그아웃 버튼 */}
                {headerActions}

                {/* 기존 알림 버튼 */}
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

                    {hasUnread && (
                        <span className="home-bell-dot" />
                    )}
                </button>

            </div>
        </header>
    );
}
