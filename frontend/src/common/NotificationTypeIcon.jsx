// 알림 종류 아이콘 (종류별 모양 · 색은 common.css의 .noti-icon)
import { viewOf } from "./notificationTypes";

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
export default function TypeIcon({ item, solid = false, size = 40 }) {
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

