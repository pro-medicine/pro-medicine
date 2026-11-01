// 확인 / 취소 화면 (#67): 친구 삭제 · 안부지킴이 해제처럼 되돌리기 어려운 동작 전에 한 번 더 묻는다
// (브라우저 기본 confirm 창 대신 화면 디자인에 맞춘 모달, 웹·모바일 공통)
import { useModalFocus } from "../common/useModalFocus";

export default function ConfirmDialog({
  title,
  message,
  confirmLabel = "확인",
  danger = false, // 삭제·해제처럼 되돌리기 어려운 동작이면 빨간 버튼
  busy = false,
  openerRef,
  onConfirm,
  onCancel,
}) {
  const dialogRef = useModalFocus(onCancel, openerRef);

  return (
    <div className="modal-layer" role="presentation" onMouseDown={onCancel}>
      <div
        ref={dialogRef}
        className="modal-card confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <span className={`confirm-dialog-icon${danger ? " is-danger" : ""}`} aria-hidden="true">
          !
        </span>
        <h2 id="confirm-dialog-title">{title}</h2>
        <p id="confirm-dialog-message">{message}</p>
        <div className="confirm-dialog-actions">
          <button type="button" className="is-cancel" data-autofocus="" disabled={busy} onClick={onCancel}>
            취소
          </button>
          <button
            type="button"
            className={danger ? "is-danger" : "is-primary"}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? "처리 중..." : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
