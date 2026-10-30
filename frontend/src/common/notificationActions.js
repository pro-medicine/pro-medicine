// 알림에서 바로 처리하는 동작 (#67): 복용 완료 / 친구 신청 수락·거절
// 알림 패널과 새 알림 팝업에서 같이 사용
import { api } from "../api/client";
import { getFollowRequests, respondFollow } from "../api/followApi";
import { updateIntake } from "../home/homeApi";

/**
 * 복용 알림의 "복용 완료": 알림 시각(scheduledAt)과 같은 시각의 아직 안 먹은 일정을 모두 체크한다.
 * (복용 알림은 같은 시각의 일정을 한 알림으로 묶어서 보냄)
 * @returns 체크한 일정 수 (0이면 이미 모두 먹음)
 */
export async function completeIntakeFromNotification(userId, item) {
  const date = item.scheduledAt.slice(0, 10); // "2026-10-30"
  const time = item.scheduledAt.slice(11, 16); // "12:30"
  const list = await api.get("/api/v1/intakes/today", { params: { date } });
  const targets = (list ?? []).filter((s) => s.remindAt === time && !s.taken);
  for (const target of targets) {
    await updateIntake(userId, target.id, true, date);
  }
  return targets.length;
}

/**
 * 친구 신청 알림의 "수락 / 거절": 알림을 보낸 사람(actorUserId)의 대기 중인 신청을 찾아 응답한다.
 * @param status "ACCEPTED" | "REJECTED"
 * @returns 처리했으면 true, 이미 처리된 신청이면 false
 */
export async function respondFollowFromNotification(userId, item, status) {
  const requests = await getFollowRequests(userId, "received");
  const request = (requests ?? []).find((r) => r.followerId === item.actorUserId);
  if (!request) return false;
  await respondFollow(userId, request.followId, status);
  return true;
}

// 알림 종류별로 어떤 버튼을 보여줄지
export function canCompleteIntake(item) {
  return item.type === "INTAKE_REMINDER" && Boolean(item.scheduledAt);
}

export function canRespondFollow(item) {
  return item.type === "NEW_FOLLOWER" && Boolean(item.actorUserId);
}
