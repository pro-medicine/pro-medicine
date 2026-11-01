// 친구 화면 오른쪽 카드 (#67 친구 시안 C+): 친구 신청 / 안부지킴이 / 안내
import { useState } from "react";
import { Icon } from "../shared/ui";
import { respondFollow } from "../api/followApi";

const MAX_REQUESTS = 3; // 카드에 바로 보여줄 받은 신청 수 (나머지는 "모두 보기")

// 받은 친구 신청: 카드에서 바로 수락 / 거절
export function RequestCard({ userId, requests, onChanged, onOpenModal }) {
  const [pendingId, setPendingId] = useState(null);
  const [error, setError] = useState("");

  const respond = async (followId, status) => {
    if (pendingId) return;
    setPendingId(followId);
    setError("");
    try {
      await respondFollow(userId, followId, status);
      onChanged(status === "ACCEPTED");
    } catch (err) {
      setError(err.message || "요청을 처리하지 못했어요.");
    } finally {
      setPendingId(null);
    }
  };

  const list = requests ?? [];

  return (
    <section className="fr-card fr-side-card" aria-label="친구 신청">
      <div className="fr-side-head">
        <h3>
          친구 신청
          {list.length > 0 && <span className="fr-count">{list.length}</span>}
        </h3>
        <button type="button" className="fr-link" onClick={(e) => onOpenModal("sent", e)}>
          보낸 신청
        </button>
      </div>

      {requests === null && <p className="fr-side-empty">불러오는 중...</p>}
      {requests !== null && list.length === 0 && (
        <p className="fr-side-empty">새로운 친구 신청이 없어요.</p>
      )}

      {list.slice(0, MAX_REQUESTS).map((item) => {
        const name = item.followerNickname ?? `사용자 #${item.followerId}`;
        const busy = pendingId === item.followId;
        return (
          <div className="fr-request" key={item.followId}>
            <div className="fr-request-who">
              <span className="fr-avatar is-md is-pink" aria-hidden="true">
                {name.charAt(0)}
              </span>
              <span>
                <strong>{name}</strong>
                <small>나에게 친구 신청을 보냈어요</small>
              </span>
            </div>
            <div className="fr-request-actions">
              <button
                type="button"
                className="is-accept"
                disabled={busy}
                onClick={() => respond(item.followId, "ACCEPTED")}
              >
                수락
              </button>
              <button
                type="button"
                className="is-reject"
                disabled={busy}
                onClick={() => respond(item.followId, "REJECTED")}
              >
                거절
              </button>
            </div>
          </div>
        );
      })}

      {list.length > MAX_REQUESTS && (
        <button type="button" className="fr-more" onClick={(e) => onOpenModal("received", e)}>
          받은 신청 {list.length}건 모두 보기
        </button>
      )}
      {error && <p className="fr-side-error">{error}</p>}
    </section>
  );
}

// 내가 지키는 친구 (안부지킴이)
export function GuardianCard({ watching, friendsById, onSelect }) {
  const list = (watching ?? []).filter((g) => g.active);
  return (
    <section className="fr-card fr-side-card" aria-label="안부지킴이">
      <div className="fr-guard-head">
        <span className="fr-guard-badge" aria-hidden="true">
          <Icon name="shield" size={20} />
        </span>
        <span>
          <h3>안부지킴이</h3>
          <small>내가 지키는 친구 {list.length}명</small>
        </span>
      </div>
      {list.length === 0 ? (
        <p className="fr-side-empty">
          아직 지키는 친구가 없어요. 친구 상세에서 안부지킴이를 신청할 수 있어요.
        </p>
      ) : (
        <ul className="fr-guard-list">
          {list.map((g) => {
            const friend = friendsById.get(g.userId);
            const name = friend?.name ?? g.nickname ?? "친구";
            return (
              <li key={g.guardianSettingId ?? g.userId}>
                <button type="button" disabled={!friend} onClick={() => friend && onSelect(friend)}>
                  <span className={`fr-avatar is-sm is-${friend?.color ?? "mint"}`} aria-hidden="true">
                    {name.charAt(0)}
                  </span>
                  <span className="fr-guard-text">
                    <strong>{name}</strong>
                    <small>{g.inactivityDays}일 미접속 시 알림</small>
                  </span>
                  {friend && <Icon name="chevron" size={16} />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

// 친구 복용 완료 알림 안내
export function FriendTipCard() {
  return (
    <section className="fr-tip">
      <span className="fr-tip-icon" aria-hidden="true">
        <Icon name="bell" size={17} />
      </span>
      <span>
        <strong>친구가 오늘 약속을 모두 지키면</strong>
        <small>알림으로 바로 알려드려요. 함께 꾸준함을 이어가 보세요.</small>
      </span>
    </section>
  );
}
