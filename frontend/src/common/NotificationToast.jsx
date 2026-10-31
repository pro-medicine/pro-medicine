// 새 알림 팝업 (#67 새 알림 팝업 시안 B + 모바일 배너)
// - 넓은 화면: 상단 종 버튼에서 꼬리가 나온 말풍선, 4초 뒤 사라짐 (마우스를 올리면 멈춤)
// - 좁은 화면(768px 이하): 화면 위에서 내려오는 배너, 위로 밀면 닫힘
// item: 알림 1개 (GET /api/v1/notifications 응답 항목)
import { useEffect, useRef, useState } from "react";
import { markNotificationRead } from "../api/notificationApi";
import {
  canCompleteIntake,
  canRespondFollow,
  completeIntakeFromNotification,
  respondFollowFromNotification,
} from "./notificationActions";
import { viewOf } from "./notificationTypes";
import TypeIcon from "./NotificationTypeIcon";

const SHOW_MS = 4000; // 버튼 없는 알림
const ACTION_SHOW_MS = 7000; // 버튼(복용 완료·수락/거절)이 있는 알림은 조금 더 오래
const RESULT_SHOW_MS = 1600; // 처리 결과 문구를 보여주는 시간
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

// 알림 종류별 버튼
// - run: 알림에서 바로 처리 (복용 완료 / 수락 / 거절) → 결과 문구를 보여주고 닫힘
// - target: 해당 화면으로 이동
function actionsOf(item, userId) {
  if (canCompleteIntake(item)) {
    return [
      {
        label: "복용 완료",
        run: async () => {
          const count = await completeIntakeFromNotification(userId, item);
          return count > 0 ? `${count}개 복용 완료했어요` : "이미 모두 챙겼어요";
        },
      },
    ];
  }
  if (item.type === "INTAKE_REMINDER") return [{ label: "체크", target: { tab: "home" } }];
  if (canRespondFollow(item)) {
    return [
      {
        label: "수락",
        run: async () =>
          (await respondFollowFromNotification(userId, item, "ACCEPTED")) ? "친구 신청을 수락했어요" : "이미 처리된 신청이에요",
      },
      {
        label: "거절",
        ghost: true,
        run: async () =>
          (await respondFollowFromNotification(userId, item, "REJECTED")) ? "친구 신청을 거절했어요" : "이미 처리된 신청이에요",
      },
    ];
  }
  if (item.type === "GUARDIAN" && item.actorUserId) {
    return [{ label: "보기", target: { tab: "friends", friendUserId: item.actorUserId } }];
  }
  return [];
}

export default function NotificationToast({ item, userId, onOpen, onNavigate, onChanged, onClose }) {
  const [mobile] = useState(() => window.matchMedia?.(MOBILE_QUERY).matches ?? false);
  const [position] = useState(() => (mobile ? null : bubblePosition()));
  const [leaving, setLeaving] = useState(false);
  const [paused, setPaused] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null); // { text, ok } 처리 결과 문구
  const touchStartY = useRef(null);
  const actions = actionsOf(item, userId);

  // 일정 시간 뒤 사라짐 (마우스를 올리거나 처리 중이면 멈춤) → 사라지는 애니메이션 후 닫기
  useEffect(() => {
    if (paused || leaving || busy) return;
    const wait = result ? RESULT_SHOW_MS : actions.length > 0 ? ACTION_SHOW_MS : SHOW_MS;
    const timer = setTimeout(() => setLeaving(true), wait);
    return () => clearTimeout(timer);
  }, [paused, leaving, busy, result, actions.length]);

  const runAction = async (action) => {
    if (action.target) {
      markNotificationRead(userId, item.notificationId).catch(() => {});
      onNavigate(action.target);
      return;
    }
    setBusy(true);
    try {
      const text = await action.run();
      setResult({ text, ok: true });
      await markNotificationRead(userId, item.notificationId).catch(() => {});
      onChanged();
    } catch (err) {
      console.error(err);
      setResult({ text: "처리하지 못했어요. 다시 시도해 주세요", ok: false });
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(onClose, 250);
    return () => clearTimeout(timer);
  }, [leaving, onClose]);

  const view = viewOf(item);

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
          {result && (
            <em className={`noti-toast-result${result.ok ? "" : " is-error"}`} role="status">
              {result.ok && "✓ "}
              {result.text}
            </em>
          )}
        </span>
      </button>
      {!result && actions.length > 0 && (
        <span className="noti-toast-actions">
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              className={`noti-toast-action${action.ghost ? " is-ghost" : ""}`}
              disabled={busy}
              onClick={() => runAction(action)}
            >
              {action.label}
            </button>
          ))}
        </span>
      )}
      {mobile && <span className="noti-toast-handle" aria-hidden="true" />}
    </div>
  );
}
