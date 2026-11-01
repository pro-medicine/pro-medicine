// 알림 종류(type)별 표시 이름 · 아이콘 이름 · 색 (#67 알림 시안 B)
// 알림 패널과 새 알림 팝업에서 같이 사용
export const TYPE_VIEW = {
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
export const viewOf = (item) => TYPE_VIEW[item.type] ?? CATEGORY_FALLBACK[item.category] ?? CATEGORY_FALLBACK.INTAKE;

