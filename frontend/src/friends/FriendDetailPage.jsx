import { useEffect, useState } from "react";
import { TopBar } from "../shared/ui";
import { getFriendDetail } from "../api/friendApi";
import ProfileScreen from "../mypage/MyPage";
import {
  getWatching,
  requestGuardian,
  removeGuardian,
} from "../api/guardianApi";

function toFriendDetail(data, fallback) {
  return {
    ...data,
    id: data.userId,
    name: data.nickname ?? "",
    initial: data.nickname?.charAt(0) ?? "?",
    color: fallback?.color ?? "mint",
    relation: data.relation ?? "친구",
    status: data.status ?? "아직 복용 기록이 없어요",
    streak: data.streak ?? 0,
    supplements: data.supplements ?? [],
    completed: data.completed ?? 0,
  };
}

export default function FriendDetailPage({
  userId,
  friendUserId,
  fallback,
  onBack,
  onNotifications,
}) {
  const [friend, setFriend] = useState(null);
  const [error, setError] = useState("");
  const [guardian, setGuardian] = useState(null);
  const [guardianBusy, setGuardianBusy] = useState(false);
  const [guardianMsg, setGuardianMsg] = useState("");

  // 친구 상세 조회
  useEffect(() => {
    let ignore = false;
    setFriend(null);
    setError("");

    getFriendDetail(userId, friendUserId)
      .then((data) => {
        if (!ignore) setFriend(toFriendDetail(data, fallback));
      })
      .catch((err) => {
        console.error(err);
        if (!ignore) setError(err.message || "친구 정보를 불러오지 못했어요.");
      });

    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, friendUserId]);

  // 이 친구에 대한 내 지킴이 상태 조회 (상세 조회와 분리: 실패해도 화면은 유지)
  useEffect(() => {
    let ignore = false;
    setGuardian(null);
    setGuardianMsg("");

    getWatching(userId)
      .then((data) => {
        if (ignore) return;
        setGuardian(
          (data ?? []).find((g) => g.userId === friendUserId) ?? null,
        );
      })
      .catch((err) => console.error(err));

    return () => {
      ignore = true;
    };
  }, [userId, friendUserId]);

  const handleGuardianRequest = async () => {
    if (guardianBusy) return;
    setGuardianBusy(true);
    setGuardianMsg("");
    try {
      const created = await requestGuardian(userId, friendUserId, 3); // 기본 3일
      setGuardian(created);
      setGuardianMsg(
        "안부지킴이를 신청했어요. 상대가 수락하면 알림을 받을 수 있어요.",
      );
    } catch (err) {
      setGuardianMsg(err.message || "신청에 실패했어요.");
    } finally {
      setGuardianBusy(false);
    }
  };

  // 수락 대기 중인 신청 취소 / 지킴이 해지
  const handleGuardianRemove = async () => {
    if (guardianBusy || !guardian) return;
    setGuardianBusy(true);
    setGuardianMsg("");
    try {
      await removeGuardian(userId, guardian.guardianSettingId);
      setGuardian(null);
    } catch (err) {
      setGuardianMsg(err.message || "처리하지 못했어요.");
    } finally {
      setGuardianBusy(false);
    }
  };

  if (error) {
    return (
      <div className="screen">
        <TopBar
          title="친구 영양정보"
          back={onBack}
          onNotifications={onNotifications}
        />
        <p className="request-empty">{error}</p>
      </div>
    );
  }

  if (!friend) {
    return (
      <div className="screen">
        <TopBar
          title="친구 영양정보"
          back={onBack}
          onNotifications={onNotifications}
        />
        <p className="request-empty">불러오는 중...</p>
      </div>
    );
  }

  return (
    <>
      <ProfileScreen
        friend={friend}
        onBack={onBack}
        onNotifications={onNotifications}
      />

      <div className="guardian-action">
        {!guardian && (
          <button
            type="button"
            className="guardian-request-button"
            disabled={guardianBusy}
            onClick={handleGuardianRequest}
          >
            🛡️ 안부지킴이 신청
          </button>
        )}

        {guardian && !guardian.active && (
          <>
            <small>안부지킴이 수락을 기다리는 중이에요</small>
            <button
              type="button"
              disabled={guardianBusy}
              onClick={handleGuardianRemove}
            >
              신청 취소
            </button>
          </>
        )}

        {guardian?.active && (
          <>
            <small>
              🛡️ 안부지킴이로 지켜보는 중 ({guardian.inactivityDays}일 미접속 시
              알림)
            </small>
            <button
              type="button"
              disabled={guardianBusy}
              onClick={handleGuardianRemove}
            >
              지킴이 해지
            </button>
          </>
        )}

        {guardianMsg && <p className="guardian-msg">{guardianMsg}</p>}
      </div>
    </>
  );
}
