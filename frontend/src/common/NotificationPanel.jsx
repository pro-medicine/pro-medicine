import { useEffect, useRef, useState } from "react";
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notificationApi";

// 탭: 전체 / 내 복용 / 친구 소식 (백엔드 NotificationCategory와 같은 값)
const TABS = [
  { id: "ALL", label: "전체", category: null },
  { id: "INTAKE", label: "내 복용", category: "INTAKE" },
  { id: "FRIEND", label: "친구 소식", category: "FRIEND" },
];

// 알림 종류(type)별 표시 이름 · 아이콘 · 색 (#67 알림 시안 B)
const TYPE_PATHS = {
  pill: (
    <>
      <path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z" />
      <path d="m8.5 8.5 7 7" />
    </>
  ),
  check: <path d="m5 12 4 4L19 6" />,
  userPlus: (
    <>
      <circle cx="9" cy="8" r="4" />
      <path d="M2 21a7 7 0 0 1 14 0M19 8v6M16 11h6" />
    </>
  ),
  people: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6" />
    </>
  ),
  shield: (
    <>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
      <path d="M12 8v4M12 16h.01" />
    </>
  ),
};
const TYPE_VIEW = {
  INTAKE_REMINDER: { label: "복용 알림", icon: "pill", tone: "mint" },
  FRIEND_GOAL_ACHIEVED: { label: "친구 완료", icon: "check", tone: "green" },
  NEW_FOLLOWER: { label: "친구 신청", icon: "userPlus", tone: "blue" },
  FOLLOW_ACCEPTED: { label: "친구 수락", icon: "people", tone: "purple" },
  GUARDIAN: { label: "안부지킴이", icon: "shield", tone: "red" },
};
// type이 없거나 모르는 값이면 category로 대신
const CATEGORY_FALLBACK = {
  INTAKE: TYPE_VIEW.INTAKE_REMINDER,
  FRIEND: { label: "친구 소식", icon: "people", tone: "purple" },
};
const viewOf = (item) => TYPE_VIEW[item.type] ?? CATEGORY_FALLBACK[item.category] ?? CATEGORY_FALLBACK.INTAKE;

function TypeIcon({ item, solid = false, size = 40 }) {
  const view = viewOf(item);
  return (
    <span
      className={`noti-icon is-${view.tone}${solid ? " is-solid" : ""}`}
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      <svg width={Math.round(size * 0.46)} height={Math.round(size * 0.46)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        {TYPE_PATHS[view.icon]}
      </svg>
    </span>
  );
}

// "2026-10-30T12:30:00" → 오늘(KST) 알림인지, "12:30"
const todayKey = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
const isToday = (createdAt) => (createdAt ?? "").slice(0, 10) === todayKey();
const hourMinute = (createdAt) => (createdAt ?? "").slice(11, 16);

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
 * - refreshKey: 값이 바뀌면 목록과 안 읽은 개수를 다시 불러옴 (새 알림 신호, 읽음 처리 후)
 * - onRead: 읽음 처리가 성공하면 호출 (App이 빨간 점을 다시 확인)
 * - onClose: 바깥 영역 또는 닫기 버튼을 누르면 호출
 */
export default function NotificationPanel({ userId = 1, anchor, refreshKey, onRead, onClose, onNavigate }) {
  const [tab, setTab] = useState("ALL");
  // 어떤 탭의 결과인지 함께 저장해서, 탭과 결과가 다르면 "불러오는 중"으로 본다.
  const [result, setResult] = useState({ tab: null, items: [], error: "" });
  const [unreadCount, setUnreadCount] = useState(0);
  const pendingIdsRef = useRef(new Set()); // 읽음 요청 중인 알림 id (같은 알림 중복 클릭 방지)

  // 안 읽은 개수: 패널이 열릴 때와 refreshKey가 바뀔 때(새 알림, 읽음 처리 후) 서버에서 다시 조회한다.
  // 이전 요청은 cleanup의 ignore로 버려서, 늦게 도착한 옛날 개수가 최신 값을 덮어쓰지 않는다.
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
  }, [userId, refreshKey]);

  // 패널이 열릴 때, 탭이 바뀔 때, refreshKey가 바뀔 때 목록을 불러온다.
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
  }, [userId, tab, refreshKey]);

  const loading = result.tab !== tab;
  // 개수 조회가 실패해도 목록에 안 읽은 알림이 보이면 "모두 읽음"을 누를 수 있게 한다.
  const hasUnreadItem = result.items.some((item) => !item.read);

  // 화면에 있는 알림의 읽음 상태만 바꾼다. (ids가 없으면 전부)
  const markItemsRead = (ids) => {
    setResult((prev) => ({
      ...prev,
      items: prev.items.map((item) =>
        !ids || ids.includes(item.notificationId) ? { ...item, read: true } : item,
      ),
    }));
  };

  // 알림 하나 읽음 처리 (안 읽은 알림만)
  const handleItemClick = async (item) => {
    const id = item.notificationId;
    if (item.read || pendingIdsRef.current.has(id)) return; // 이미 읽음 또는 요청 중
    pendingIdsRef.current.add(id);
    try {
      await markNotificationRead(userId, id);
      markItemsRead([id]);
      setUnreadCount((count) => Math.max(0, count - 1)); // 화면 먼저 반영, 정확한 값은 onRead → refreshKey로 다시 조회
      onRead?.();
    } catch (err) {
      console.error(err);   // 실패하면 화면은 그대로 둔다 (다음에 다시 시도 가능)
    } finally {
      pendingIdsRef.current.delete(id);
    }
  };

  // 상단 고정 카드의 버튼: 읽음 처리 후 해당 화면으로 이동 (패널은 App이 닫음)
  const readAndGo = (item, target) => {
    handleItemClick(item);
    onNavigate?.(target);
  };

  // 상단에 고정할 중요 알림 (안 읽은 것 중 가장 최근 1개씩)
  // - 안부지킴이: 친구가 오래 접속하지 않음 → 빨간 카드
  // - 오늘 복용 알림: 지금 먹을 영양제 → 민트 카드
  const items = result.items;
  const guardianAlert = tab !== "INTAKE" ? items.find((i) => !i.read && i.type === "GUARDIAN") : null;
  const intakeAlert =
    tab !== "FRIEND" ? items.find((i) => !i.read && i.type === "INTAKE_REMINDER" && isToday(i.createdAt)) : null;
  const pinnedIds = [guardianAlert, intakeAlert].filter(Boolean).map((i) => i.notificationId);
  const others = items.filter((i) => !pinnedIds.includes(i.notificationId));

  // Esc 키로 닫기 (패널 바깥을 눌러도 닫힘)
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape") onClose?.();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // 모두 읽음
  const handleReadAll = async () => {
    try {
      await markAllNotificationsRead(userId);
      markItemsRead(null);
      setUnreadCount(0);
      onRead?.();
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
          <h2>
            알림
            {unreadCount > 0 && <em aria-label={`안 읽은 알림 ${unreadCount}개`}>{unreadCount}</em>}
          </h2>
          <button
            type="button"
            className="read-all"
            onClick={handleReadAll}
            disabled={loading || (unreadCount === 0 && !hasUnreadItem)}
          >
            모두 읽음
          </button>
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
            </button>
          ))}
        </div>

        <div className="notification-list">
          {loading && <p className="notification-state">알림을 불러오는 중...</p>}
          {!loading && result.error && (
            <p className="notification-state">{result.error}</p>
          )}
          {!loading && !result.error && items.length === 0 && (
            <p className="notification-state">새로운 알림이 없어요</p>
          )}

          {/* 중요 알림 상단 고정 */}
          {!loading && !result.error && guardianAlert && (
            <section className="noti-pin is-guardian" aria-label="안부 확인이 필요한 알림">
              <div className="noti-pin-body">
                <TypeIcon item={guardianAlert} solid />
                <div>
                  <span className="noti-pin-label">안부 확인이 필요해요</span>
                  <p>{guardianAlert.title}</p>
                  <span className="notification-meta">{timeAgo(guardianAlert.createdAt)}</span>
                </div>
              </div>
              <div className="noti-pin-actions">
                {guardianAlert.actorUserId && (
                  <button
                    type="button"
                    className="is-primary"
                    onClick={() => readAndGo(guardianAlert, { tab: "friends", friendUserId: guardianAlert.actorUserId })}
                  >
                    친구 보기
                  </button>
                )}
                <button type="button" onClick={() => handleItemClick(guardianAlert)}>
                  나중에
                </button>
              </div>
            </section>
          )}
          {!loading && !result.error && intakeAlert && (
            <section className="noti-pin is-intake" aria-label="지금 먹을 영양제 알림">
              <TypeIcon item={intakeAlert} solid />
              <div className="noti-pin-text">
                <p>{intakeAlert.title}</p>
                <span className="notification-meta">{hourMinute(intakeAlert.createdAt)} 알림 · 홈에서 체크해요</span>
              </div>
              <button type="button" onClick={() => readAndGo(intakeAlert, { tab: "home" })}>
                체크하기
              </button>
            </section>
          )}

          {!loading && !result.error && pinnedIds.length > 0 && others.length > 0 && (
            <h3 className="noti-section-title">다른 소식</h3>
          )}
          {!loading &&
            !result.error &&
            others.map((item) => {
              const view = viewOf(item);
              const unread = !item.read;
              return (
                <article
                  className={unread ? "notification-item unread" : "notification-item"}
                  key={item.notificationId}
                  role="button"
                  tabIndex={0}
                  aria-label={`${view.label}: ${item.title}${unread ? ", 안 읽음" : ""}`}
                  onClick={() => handleItemClick(item)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault(); // Space로 페이지가 스크롤되지 않게
                      handleItemClick(item);
                    }
                  }}
                >
                  <TypeIcon item={item} size={36} />
                  <div>
                    <h3>{item.title}</h3>
                    <span className="notification-meta">
                      {view.label} · {timeAgo(item.createdAt)}
                    </span>
                  </div>
                  {unread && <i />}
                </article>
              );
            })}
        </div>
      </aside>
    </div>
  );
}
