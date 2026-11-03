package com.ucamp.app.domain.notification.event;

import java.time.LocalDate;

/**
 * 복용 체크 또는 체크 해제가 저장됐다는 이벤트. (IntakeService.updateIntake에서 둘 다 발행)
 * 알림 쪽에서 받아 본인의 다른 화면·기기에 "다시 조회" 신호(SSE refresh)를 보낸다.
 *
 * IntakeCheckedEvent(체크할 때만)와 따로 둔 이유: IntakeCheckedEvent는 친구 복용 완료 알림에도 쓰이므로,
 * 체크 해제에 재사용하면 친구에게 잘못된 완료 알림이 갈 수 있다.
 *
 * @param userId 복용 체크·해제한 사용자
 * @param date   체크·해제한 날짜 (KST)
 */
public record IntakeStateChangedEvent(Long userId, LocalDate date) {
}
