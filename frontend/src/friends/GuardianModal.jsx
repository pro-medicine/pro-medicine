import { useState } from "react";
import { useModalFocus } from "../common/useModalFocus";

const DAY_OPTIONS = [1, 3, 5, 7, 14, 30];

export default function GuardianDaysModal({
  name,
  currentDays,
  busy,
  openerRef,
  onClose,
  onChange, // (days) => void
  onCancelGuardian, // () => void
}) {
  const dialogRef = useModalFocus(onClose, openerRef);
  const [days, setDays] = useState(currentDays);

  return (
    <div className="modal-layer" role="presentation" onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-label="안부지킴이 설정"
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="section-label">안부지킴이</span>
            <h2>안부지킴이 설정</h2>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>

        <p className="modal-description">
          {name}님이 설정한 기간 동안 앱에 접속하지 않으면 알림을 받아요.
        </p>

        <div
          className="days-picker"
          role="radiogroup"
          aria-label="미접속 알림 일수"
        >
          {DAY_OPTIONS.map((d) => (
            <button
              key={d}
              type="button"
              role="radio"
              aria-checked={days === d}
              data-autofocus={days === d ? "" : undefined}
              className={days === d ? "active" : ""}
              onClick={() => setDays(d)}
            >
              {d}일
            </button>
          ))}
        </div>

        <div className="modal-actions">
          <button
            type="button"
            className="danger"
            disabled={busy}
            onClick={onCancelGuardian}
          >
            안부지킴이 해지
          </button>
          <button
            type="button"
            className="primary"
            disabled={busy || days === currentDays}
            onClick={() => onChange(days)}
          >
            저장
          </button>
        </div>
      </div>
    </div>
  );
}
