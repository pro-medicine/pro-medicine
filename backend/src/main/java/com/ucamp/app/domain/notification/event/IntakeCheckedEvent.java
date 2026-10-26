package com.ucamp.app.domain.notification.event;

import java.time.LocalDate;

/**
 * 사용자가 복용 체크(TAKEN)를 했다는 이벤트.
 * 복용 체크를 저장하는 쪽(홈)에서 발행하고, 알림 쪽에서 받아 친구 복용 완료 알림을 만든다.
 *
 * @param userId 복용 체크한 사용자
 * @param date   복용 체크한 날짜 (KST)
 */
public record IntakeCheckedEvent(Long userId, LocalDate date) {
}
