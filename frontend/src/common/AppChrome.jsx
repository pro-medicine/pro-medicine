import { Icon } from "../shared/ui";

const NAV_ITEMS = [
    { id: "home", label: "홈", icon: "home" },
    { id: "friends", label: "친구 영양정보", icon: "people" },
    { id: "disposal", label: "폐의약품 지도", icon: "pin" },
    { id: "my", label: "마이페이지", icon: "user" },
];

/**
 * 웹 상단 메뉴 (넓은 화면 전용, 768px 이하에서는 숨기고 하단 탭을 사용)
 * @param {{ tab: string, onChange: (tab: string) => void, hasUnread: boolean, onNotifications: (event) => void }} props
 */
export function TopNav({ tab, onChange, hasUnread, onNotifications }) {
    return (
        <header className="top-nav">
            <div className="top-nav-inner">
                <button type="button" className="top-nav-logo" onClick={() => onChange("home")}>
                    <span className="top-nav-mark" aria-hidden="true" />
                    약쏙해줘
                </button>

                <nav className="top-nav-menu" aria-label="주요 메뉴">
                    {NAV_ITEMS.map((item) => (
                        <button
                            type="button"
                            key={item.id}
                            className={tab === item.id ? "active" : ""}
                            aria-current={tab === item.id ? "page" : undefined}
                            onClick={() => onChange(item.id)}
                        >
                            {item.label}
                        </button>
                    ))}
                </nav>

                <div className="top-nav-actions">
                    <button
                        type="button"
                        className="top-nav-bell"
                        aria-label="알림 열기"
                        onClick={onNotifications}
                    >
                        <Icon name="bell" size={19} />
                        {hasUnread && <span className="top-nav-dot" />}
                    </button>
                    <button
                        type="button"
                        className={`top-nav-avatar${tab === "my" ? " active" : ""}`}
                        aria-label="마이페이지"
                        onClick={() => onChange("my")}
                    >
                        <Icon name="user" size={18} />
                    </button>
                </div>
            </div>
        </header>
    );
}

/**
 * 하단 탭 네비게이션
 * @param {{ tab: "home"|"friends"|"disposal"|"my", onChange: (tab: string) => void }} props
 */
export function BottomNav({ tab, onChange }) {
    return (
        <nav className="bottom-nav" aria-label="주요 메뉴">
            {NAV_ITEMS.map((item) => (
                <button
                    className={tab === item.id ? "active" : ""}
                    key={item.id}
                    onClick={() => onChange(item.id)}
                >
          <span className="nav-icon">
            <Icon name={item.icon} size={21} />
          </span>
                    <span>{item.label}</span>
                </button>
            ))}
        </nav>
    );
}