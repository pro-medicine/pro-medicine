import { useEffect, useRef, useState } from "react";
import ConfirmDialog from "../friends/ConfirmDialog";
import "./withdrawal.css";

export default function WithdrawalSection({
  onWithdraw,
  busy,
  onConfirmOpenChange,
}) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState("");
  // 최종 확인 모달 상태
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const passwordInputRef = useRef(null);
  const withdrawalLinkRef = useRef(null);
  const submitButtonRef = useRef(null);
  const hasOpenedRef = useRef(false);
  // 확인 창을 연 버튼 (닫으면 포커스 복원)
  const confirmOpenerRef = useRef(null);

  useEffect(() => {
    if (open) {
      hasOpenedRef.current = true;
      passwordInputRef.current?.focus();
    } else if (hasOpenedRef.current) {
      withdrawalLinkRef.current?.focus();
    }
  }, [open]);

  // 부모(설정 창)가 Esc로 설정 창을 닫지 않도록 확인 창 열림 상태를 알려줌
  useEffect(() => {
    onConfirmOpenChange?.(confirmOpen);
    return () => onConfirmOpenChange?.(false);
  }, [confirmOpen, onConfirmOpenChange]);

  // 폼 제출: 바로 탈퇴하지 않고 확인 모달을 엶
  const submit = (event) => {
    event.preventDefault();
    if (!password || !confirmed || busy || submitting) return;
    setError("");
    confirmOpenerRef.current = submitButtonRef.current;
    setConfirmOpen(true);
  };

  // 확인 모달의 [탈퇴]: 실제 탈퇴 요청
  const confirmWithdraw = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      await onWithdraw(password);
    } catch (cause) {
      setConfirmOpen(false);
      setError(cause.message || "회원 탈퇴 요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const cancelConfirm = () => {
    // 처리 중에는 바깥을 누르거나 Esc를 눌러도 닫지 않음
    if (!submitting) setConfirmOpen(false);
  };

  return (
    <section className="withdrawal-section">
      {!open ? (
        <button
          ref={withdrawalLinkRef}
          type="button"
          className="withdrawal-link"
          onClick={() => setOpen(true)}
        >
          회원 탈퇴
        </button>
      ) : (
        <form onSubmit={submit} className="withdrawal-form">
          <strong>회원 탈퇴</strong>
          <p>
            계정과 복용 기록, 건강 정보, 친구 관계, 설문 및 추천 기록이 삭제되며
            복구할 수 없습니다.
          </p>
          <label htmlFor="withdrawal-password">현재 비밀번호 확인</label>
          <input
            ref={passwordInputRef}
            id="withdrawal-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              setError("");
            }}
            required
          />
          <label className="withdrawal-check">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            위 내용을 확인했으며 회원 탈퇴에 동의합니다.
          </label>
          {error && (
            <p role="alert" className="withdrawal-error">
              {error}
            </p>
          )}
          <div className="withdrawal-buttons">
            <button
              type="button"
              className="btn btn-outline btn-sm"
              disabled={busy}
              onClick={() => {
                setOpen(false);
                setPassword("");
                setConfirmed(false);
                setError("");
              }}
            >
              취소
            </button>
            <button
              ref={submitButtonRef}
              type="submit"
              className="withdrawal-danger"
              disabled={busy || !confirmed || !password}
            >
              {busy ? "탈퇴 처리 중..." : "탈퇴 확정"}
            </button>
          </div>
        </form>
      )}

      {confirmOpen && (
        <ConfirmDialog
          title="회원 탈퇴"
          message="정말 탈퇴하시겠습니까? 삭제한 계정은 복구할 수 없습니다."
          confirmLabel="탈퇴"
          danger
          busy={submitting || busy}
          openerRef={confirmOpenerRef}
          onConfirm={confirmWithdraw}
          onCancel={cancelConfirm}
        />
      )}
    </section>
  );
}
