import { useState } from "react";
import "./withdrawal.css";

export default function WithdrawalSection({ onWithdraw, busy }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    if (!password || !confirmed || busy) return;
    if (!window.confirm("정말 탈퇴하시겠습니까? 삭제한 계정은 복구할 수 없습니다.")) return;
    setError("");
    try {
      await onWithdraw(password);
    } catch (cause) {
      setError(cause.message || "회원 탈퇴 요청에 실패했습니다.");
    }
  };

  return (
    <section className="withdrawal-section">
      {!open ? (
        <button type="button" className="withdrawal-link" onClick={() => setOpen(true)}>
          회원 탈퇴
        </button>
      ) : (
        <form onSubmit={submit} className="withdrawal-form">
          <strong>회원 탈퇴</strong>
          <p>계정과 복용 기록, 건강 정보, 친구 관계, 설문 및 추천 기록이 삭제되며 복구할 수 없습니다.</p>
          <label htmlFor="withdrawal-password">현재 비밀번호 확인</label>
          <input id="withdrawal-password" type="password" autoComplete="current-password"
            value={password} onChange={(event) => setPassword(event.target.value)} required />
          <label className="withdrawal-check">
            <input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
            위 내용을 확인했으며 회원 탈퇴에 동의합니다.
          </label>
          {error && <p role="alert" className="withdrawal-error">{error}</p>}
          <div className="withdrawal-buttons">
            <button type="button" className="btn btn-outline btn-sm" disabled={busy}
              onClick={() => { setOpen(false); setPassword(""); setConfirmed(false); setError(""); }}>취소</button>
            <button type="submit" className="withdrawal-danger" disabled={busy || !confirmed || !password}>
              {busy ? "탈퇴 처리 중..." : "탈퇴 확정"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
