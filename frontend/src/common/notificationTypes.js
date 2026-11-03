// 알림 종류(type)별 표시 이름 · 아이콘 이름 · 색 (#67 알림 시안 B)
// 알림 패널과 새 알림 팝업에서 같이 사용
export const TYPE_VIEW = {
  INTAKE_REMINDER: { label: "복용 알림", icon: "pill", tone: "mint" },
  INTAKE_MISSED: { label: "복용 놓침", icon: "clock", tone: "orange" },
  FRIEND_GOAL_ACHIEVED: { label: "친구 완료", icon: "check", tone: "green" },
  NEW_FOLLOWER: { label: "친구 신청", icon: "userPlus", tone: "blue" },
  FOLLOW_ACCEPTED: { label: "친구 수락", icon: "people", tone: "purple" },
  FRIEND_NUDGE: { label: "챙겨주기", icon: "heart", tone: "pink" },
  GUARDIAN: { label: "안부지킴이", icon: "shield", tone: "red" },
  GUARDIAN_REQUESTED: { label: "안부지킴이 신청", icon: "shield", tone: "blue" },
  GUARDIAN_ACCEPTED: { label: "안부지킴이 수락", icon: "shield", tone: "green" },
};

// 복용 알림은 복용 시각부터 1시간, 복용 놓침 알림은 2시간까지만 보인다 (백엔드 NotificationService와 같은 기준)
// 서버가 목록에서 빼 주지만, 패널을 열어 둔 채 시간이 지나는 경우를 위해 화면에서도 한 번 더 거른다.
const VISIBLE_MINUTES = { INTAKE_REMINDER: 60, INTAKE_MISSED: 120 };
export function isExpired(item, now = new Date()) {
  const minutes = VISIBLE_MINUTES[item.type];
  if (!minutes || !item.scheduledAt) return false;
  // scheduledAt은 한국 시간 문자열 ("2026-11-03T12:30:00") → +09:00을 붙여 정확한 시각으로 바꾼다
  const scheduled = new Date(`${item.scheduledAt.slice(0, 19)}+09:00`);
  return now - scheduled >= minutes * 60000;
}
// type이 없거나 모르는 값이면 category로 대신
const CATEGORY_FALLBACK = {
  INTAKE: TYPE_VIEW.INTAKE_REMINDER,
  FRIEND: { label: "친구 소식", icon: "people", tone: "purple" },
};
export const viewOf = (item) => TYPE_VIEW[item.type] ?? CATEGORY_FALLBACK[item.category] ?? CATEGORY_FALLBACK.INTAKE;

