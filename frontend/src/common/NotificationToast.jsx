// 새 알림 팝업 (#67 새 알림 팝업 시안 B + 모바일 배너)
// - 넓은 화면: 상단 종 버튼에서 꼬리가 나온 말풍선, 4초 뒤 사라짐 (마우스를 올리면 멈춤)
// - 좁은 화면(768px 이하): 화면 위에서 내려오는 배너, 위로 밀면 닫힘
// item: 알림 1개 (GET /api/v1/notifications 응답 항목)
import { useEffect, useRef, useState } from "react";
import { viewOf } from "./notificationTypes";
import TypeIcon from "./NotificationTypeIcon";

const SHOW_MS = 4000;
const MOBILE_QUERY = "(max-width: 768px)";

// 종 버튼 아래 말풍선 위치 (종 가운데에 꼬리가 오도록)
function bubblePosition() {
  const bell = document.querySelector(".top-nav-bell");
  const rect = bell?.getBoundingClientRect();
  if (!rect || rect.width === 0) return null;
  const right = Math.max(16, window.innerWidth - rect.right - 8);
  const tailRight = window.innerWidth - (rect.left + rect.width / 2) - right - 7;
  return { top: rect.bottom + 12, right, tailRight };
}

// 알림 종류별 바로가기 버튼 (없으면 null)
function actionOf(item) {
  if (item.type === "INTAKE_REMINDER") return { label: "체크", target: { tab: "home" } };
  if (item.type === "GUARDIAN" && item.actorUserId) {
    return { label: "보기", target: { tab: "friends", friendUserId: item.actorUserId } };
  }
  return null;
}

export default function NotificationToast({ item, onOpen, onAction, onClose }) {
  const [mobile] = useState(() => window.matchMedia?.(MOBILE_QUERY).matches ?? false);
  const [position] = useState(() => (mobile ? null : bubblePosition()));
  const [leaving, setLeaving] = useState(false);
  const [paused, setPaused] = useState(false);
  const touchStartY = useRef(null);

  // 4초 뒤 사라짐 (마우스를 올리면 멈춤) → 사라지는 애니메이션 후 닫기
  useEffect(() => {
    if (paused || leaving) return;
    const timer = setTimeout(() => setLeaving(true), SHOW_MS);
    return () => clearTimeout(timer);
  }, [paused, leaving]);

  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(onClose, 250);
    return () => clearTimeout(timer);
  }, [leaving, onClose]);

  const view = viewOf(item);
  const action = actionOf(item);

  // 모바일: 위로 밀면 닫기
  const onTouchStart = (e) => {
    touchStartY.current = e.touches[0].clientY;
  };
  const onTouchMove = (e) => {
    if (touchStartY.current !== null && e.touches[0].clientY - touchStartY.current < -30) {
      touchStartY.current = null;
      setLeaving(true);
    }
  };

  const className = [
    "noti-toast",
    mobile ? "is-banner" : "is-bubble",
    leaving ? "is-leaving" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={className}
      role="status"
      aria-live="polite"
      style={
        position
          ? { top: position.top, right: position.right, "--tail-right": `${position.tailRight}px` }
          : undefined
      }
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onTouchStart={mobile ? onTouchStart : undefined}
      onTouchMove={mobile ? onTouchMove : undefined}
    >
      <button type="button" className="noti-toast-main" onClick={onOpen} aria-label={`새 알림: ${item.title}. 알림 목록 열기`}>
        <TypeIcon item={item} size={38} />
        <span className="noti-toast-text">
          <small>{view.label} · 방금</small>
          <strong>{item.title}</strong>
        </span>
      </button>
      {action && (
        <button type="button" className="noti-toast-action" onClick={() => onAction(item, action.target)}>
          {action.label}
        </button>
      )}
      {mobile && <span className="noti-toast-handle" aria-hidden="true" />}
    </div>
  );
}
