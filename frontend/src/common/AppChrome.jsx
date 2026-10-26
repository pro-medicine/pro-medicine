import { Icon } from "../shared/ui";

export function BottomNav({ tab, onChange }) {
    const items = [
        { id: "home", label: "홈", icon: "home" },
        { id: "friends", label: "친구 영양정보", icon: "people" },
        { id: "disposal", label: "폐의약품 지도", icon: "pin" },
        { id: "my", label: "마이페이지", icon: "user" },
    ];

    return (
        <nav className="bottom-nav" aria-label="주요 메뉴">
            {items.map((item) => (
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

export function NotificationPanel({ onClose }) {
    const notifications = [
        {
            type: "복용 알림",
            time: "방금",
            title: "점심 영양제, 챙겨 드셨나요?",
            text: "오메가3 1캡슐을 먹을 시간이에요.",
            icon: "clock",
            unread: true,
        },
        {
            type: "친구 소식",
            time: "10분 전",
            title: "김서준님이 오늘의 약속을 완료했어요",
            text: "3개의 영양제를 모두 챙겨 먹었어요.",
            icon: "people",
            unread: true,
        },
        {
            type: "친구 소식",
            time: "1시간 전",
            title: "이유진님이 점심 영양제를 챙겼어요",
            text: "오늘 2개 중 1개를 복용했어요.",
            icon: "people",
            unread: false,
        },
        {
            type: "복용 기록",
            time: "어제",
            title: "어제의 복용률은 100%예요",
            text: "연속 복용 12일째를 달성했어요.",
            icon: "check",
            unread: false,
        },
    ];

    return (
        <div className="notification-layer" role="presentation" onMouseDown={onClose}>
            <aside
                className="notification-panel"
                aria-label="알림 목록"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className="notification-header">
                    <div>
                        <span className="section-label">NOTIFICATIONS</span>
                        <h2>새로운 알림</h2>
                    </div>
                    <button aria-label="닫기" onClick={onClose}>×</button>
                </div>
                <div className="notification-tabs">
                    <button className="active">전체 <span>2</span></button>
                    <button>내 복용</button>
                    <button>친구 소식</button>
                </div>
                <div className="notification-list">
                    {notifications.map((item) => (
                        <article className={item.unread ? "notification-item unread" : "notification-item"} key={item.title}>
              <span className={`notification-type-icon ${item.icon}`}>
                <Icon name={item.icon} size={19} />
              </span>
                            <div>
                                <span className="notification-meta">{item.type} · {item.time}</span>
                                <h3>{item.title}</h3>
                                <p>{item.text}</p>
                            </div>
                            {item.unread && <i />}
                        </article>
                    ))}
                </div>
                <button className="read-all">모두 읽음으로 표시</button>
            </aside>
        </div>
    );
}