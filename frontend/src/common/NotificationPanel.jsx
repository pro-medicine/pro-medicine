import { useEffect, useState } from "react";
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notificationApi";
import { Icon } from "../shared/ui";

// 탭: 전체 / 내 복용 / 친구 소식 (백엔드 NotificationCategory와 같은 값)
const TABS = [
  { id: "ALL", label: "전체", category: null },
  { id: "INTAKE", label: "내 복용", category: "INTAKE" },
  { id: "FRIEND", label: "친구 소식", category: "FRIEND" },
];

// 알림 종류(category)별 표시 이름과 아이콘
const CATEGORY_VIEW = {
  INTAKE: { label: "복용 알림", icon: "clock" },
  FRIEND: { label: "친구 소식", icon: "people" },
};

/**
 * 알림 시각을 "방금", "10분 전", "3시간 전", "어제", "3일 전", "10월 20일"로 바꾼다.
 * createdAt은 서버가 보내는 한국 시간 문자열 (예: "2026-10-26T14:05:00")
 */
function timeAgo(createdAt, now = new Date()) {
  const created = new Date(createdAt);
  const minutes = Math.floor((now - created) / 60000);

  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 60 * 24 && created.getDate() === now.getDate()) {
    return `${Math.floor(minutes / 60)}시간 전`;
  }

  // 날짜(자정 기준)로 며칠 전인지 계산
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const days = Math.round((startOfDay(now) - startOfDay(created)) / 86400000);
  if (days === 1) return "어제";
  if (days < 7) return `${days}일 전`;
  return `${created.getMonth() + 1}월 ${created.getDate()}일`;
}

/**
 * 알림 패널 (종 아이콘을 누르면 열림)
 * - userId: 로그인 연동 전까지 App에서 임시 id를 넘겨받는다.
 * - anchor: 패널을 띄울 위치 { top, right } (종 버튼 바로 아래). 없으면 화면 오른쪽 위
 * - onClose: 바깥 영역 또는 닫기 버튼을 누르면 호출
 */
export default function NotificationPanel({ userId = 1, anchor, onClose }) {
  const [tab, setTab] = useState("ALL");
  // 어떤 탭의 결과인지 함께 저장해서, 탭과 결과가 다르면 "불러오는 중"으로 본다.
  const [result, setResult] = useState({ tab: null, items: [], error: "" });
  const [unreadCount, setUnreadCount] = useState(0);

  // 안 읽은 개수: 패널이 열릴 때 한 번 불러오고, 읽음 처리할 때는 화면에서 직접 줄인다.
  useEffect(() => {
    let ignore = false;
    getUnreadCount(userId)
      .then((data) => {
        if (!ignore) setUnreadCount(data?.unreadCount ?? 0);
      })
      .catch((err) => console.error(err));
    return () => {
      ignore = true;
    };
  }, [userId]);

  // 패널이 열릴 때와 탭이 바뀔 때 목록을 불러온다.
  useEffect(() => {
    let ignore = false;   // 패널이 닫히거나 탭이 바뀐 뒤 늦게 온 응답은 버린다.
    const category = TABS.find((t) => t.id === tab).category;

    getNotifications(userId, category)
      .then((items) => {
        if (!ignore) setResult({ tab, items: items ?? [], error: "" });
      })
      .catch((err) => {
        console.error(err);
        if (!ignore) {
          setResult({ tab, items: [], error: "알림을 불러오지 못했어요." });
        }
      });

    return () => {
      ignore = true;
    };
  }, [userId, tab]);

  const loading = result.tab !== tab;

  // 화면에 있는 알림의 읽음 상태만 바꾼다. (ids가 없으면 전부)
  const markItemsRead = (ids) => {
    setResult((prev) => ({
      ...prev,
      items: prev.items.map((item) =>
        !ids || ids.includes(item.notificationId) ? { ...item, read: true } : item,
      ),
    }));
  };

  // 알림 하나 클릭 → 안 읽은 알림이면 읽음 처리
  const handleItemClick = async (item) => {
    if (item.read) return;
    try {
      await markNotificationRead(userId, item.notificationId);
      markItemsRead([item.notificationId]);
      setUnreadCount((count) => Math.max(0, count - 1));
    } catch (err) {
      console.error(err);   // 실패하면 화면은 그대로 둔다 (다음에 다시 시도 가능)
    }
  };

  // 모두 읽음
  const handleReadAll = async () => {
    try {
      await markAllNotificationsRead(userId);
      markItemsRead(null);
      setUnreadCount(0);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="notification-layer" role="presentation" onMouseDown={onClose}>
      <aside
        className="notification-panel"
        style={anchor ? { top: anchor.top, right: anchor.right } : undefined}
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

        <div className="notification-tabs" role="tablist">
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              className={tab === t.id ? "active" : ""}
              onClick={() => setTab(t.id)}
            >
              {t.label}
              {t.id === "ALL" && unreadCount > 0 && <span>{unreadCount}</span>}
            </button>
          ))}
        </div>

        <div className="notification-list">
          {loading && <p className="notification-state">알림을 불러오는 중...</p>}
          {!loading && result.error && (
            <p className="notification-state">{result.error}</p>
          )}
          {!loading && !result.error && result.items.length === 0 && (
            <p className="notification-state">새로운 알림이 없어요</p>
          )}

          {!loading &&
            !result.error &&
            result.items.map((item) => {
              const view = CATEGORY_VIEW[item.category] ?? CATEGORY_VIEW.INTAKE;
              const unread = !item.read;
              return (
                <article
                  className={unread ? "notification-item unread" : "notification-item"}
                  key={item.notificationId}
                  role="button"
                  tabIndex={0}
                  onClick={() => handleItemClick(item)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") handleItemClick(item);
                  }}
                >
                  <span className={`notification-type-icon ${view.icon}`}>
                    <Icon name={view.icon} size={19} />
                  </span>
                  <div>
                    <span className="notification-meta">
                      {view.label} · {timeAgo(item.createdAt)}
                    </span>
                    <h3>{item.title}</h3>
                  </div>
                  {unread && <i />}
                </article>
              );
            })}
        </div>
        <button
          className="read-all"
          onClick={handleReadAll}
          disabled={unreadCount === 0}
        >
          모두 읽음으로 표시
        </button>
      </aside>
    </div>
  );
}
