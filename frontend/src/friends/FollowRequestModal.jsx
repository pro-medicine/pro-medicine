import {
  getFollowRequests,
  respondFollow,
  cancelFollow,
} from "../api/followApi";
import { useEffect, useState } from "react";
import { useModalFocus } from "../common/useModalFocus";

const TABS = [
  { id: "received", label: "받은 신청" },
  { id: "sent", label: "보낸 신청" },
];

export default function FollowRequestModal({
  userId,
  onClose,
  onAccepted,
  openerRef,
}) {
  const [tab, setTab] = useState("received");
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const dialogRef = useModalFocus(onClose, openerRef);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError("");
    setItems([]);
    getFollowRequests(userId, tab)
      .then((data) => {
        if (!ignore) setItems(data ?? []);
      })
      .catch((err) => {
        console.error(err);
        if (!ignore) setError(err.message || "신청 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [userId, tab]);

  const removeItem = (followId) =>
    setItems((prev) => prev.filter((i) => i.followId !== followId));

  const handleRespond = async (followId, status) => {
    if (pendingId) return;
    setPendingId(followId);
    setError("");
    try {
      await respondFollow(userId, followId, status);
      removeItem(followId);
      if (status === "ACCEPTED") onAccepted?.();
    } catch (err) {
      console.error(err);
      setError(err.message || "요청을 처리하지 못했어요.");
    } finally {
      setPendingId(null);
    }
  };

  const handleCancel = async (followId) => {
    if (pendingId) return;
    setPendingId(followId);
    setError("");
    try {
      await cancelFollow(userId, followId);
      removeItem(followId);
    } catch (err) {
      console.error(err);
      setError(err.message || "신청을 취소하지 못했어요.");
    } finally {
      setPendingId(null);
    }
  };

  const getPerson = (item) => {
    const isReceived = tab === "received";
    const id = isReceived ? item.followerId : item.followingId;
    const nickname = isReceived
      ? item.followerNickname
      : item.followingNickname;
    return { id, name: nickname ?? `사용자 #${id}` };
  };

  return (
    <div className="modal-layer" role="presentation" onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className="modal-card"
        aria-modal="true"
        role="dialog"
        aria-label="친구 신청 목록"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="section-label">FRIEND REQUESTS</span>
            <h2>친구 신청</h2>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>

        <div className="request-tabs" role="tablist">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              className={tab === t.id ? "active" : ""}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="request-list">
          {loading && <p className="request-empty">불러오는 중...</p>}
          {!loading && items.length === 0 && !error && (
            <p className="request-empty">
              {tab === "received"
                ? "받은 친구 신청이 없어요."
                : "보낸 친구 신청이 없어요."}
            </p>
          )}

          {!loading &&
            items.map((item) => {
              const person = getPerson(item);
              const pending = pendingId === item.followId;
              return (
                <div className="request-row" key={item.followId}>
                  <span className="avatar mint">{person.name.charAt(0)}</span>
                  <span className="request-name">
                    <strong>{person.name}</strong>
                    <small>
                      {tab === "received"
                        ? "팔로우를 신청했어요"
                        : "수락을 기다리는 중"}
                    </small>
                  </span>

                  {tab === "received" ? (
                    <span className="request-actions">
                      <button
                        type="button"
                        className="accept"
                        disabled={pending}
                        onClick={() => handleRespond(item.followId, "ACCEPTED")}
                      >
                        수락
                      </button>
                      <button
                        type="button"
                        className="reject"
                        disabled={pending}
                        onClick={() => handleRespond(item.followId, "REJECTED")}
                      >
                        거절
                      </button>
                    </span>
                  ) : (
                    <span className="request-actions">
                      <button
                        type="button"
                        className="reject"
                        disabled={pending}
                        onClick={() => handleCancel(item.followId)}
                      >
                        신청 취소
                      </button>
                    </span>
                  )}
                </div>
              );
            })}
        </div>

        {error && <p className="modal-result error">{error}</p>}
      </div>
    </div>
  );
}
