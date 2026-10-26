import { requestFollow } from "../api/followApi";
import { useEffect, useState } from "react";

export default function AddFriendModal({ userId, onClose }) {
  const [nickname, setNickname] = useState("");
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState(null); // { type: "success" | "error", text }

  // ESC로 닫기
  useEffect(() => {
    const onKeyDown = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = nickname.trim();
    if (!trimmed || sending) return;

    setSending(true);
    setResult(null);

    try {
      await requestFollow(userId, trimmed);
      setResult({
        type: "success",
        text: `${trimmed}님에게 팔로우를 신청했어요. 상대가 수락하면 친구 목록에 나타나요.`,
      });
      setNickname("");
    } catch (err) {
      console.error(err);
      setResult({
        type: "error",
        text: err.response?.data?.message || err.message,
      });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="modal-layer" role="presentation" onMouseDown={onClose}>
      <div
        className="modal-card"
        role="dialog"
        aria-label="친구 추가"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="section-label">ADD FRIEND</span>
            <h2>친구 추가</h2>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>

        <p className="modal-description">
          친구의 닉네임을 입력하고 팔로우를 신청해보세요. 상대가 승인하면 서로의
          영양정보를 볼 수 있어요.
        </p>

        <form className="modal-form" onSubmit={handleSubmit}>
          <input
            aria-label="닉네임"
            autoFocus
            onChange={(e) => setNickname(e.target.value)}
            placeholder="친구 닉네임을 입력해주세요"
            value={nickname}
          />
          <button type="submit" disabled={sending || !nickname.trim()}>
            {sending ? "신청 중..." : "팔로우"}
          </button>
        </form>

        {result && (
          <p className={`modal-result ${result.type}`}>{result.text}</p>
        )}
      </div>
    </div>
  );
}
