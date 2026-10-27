import { useCallback, useEffect, useState } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriendDetail, deleteFriend } from "../api/friendApi";
import ProfileScreen from "../mypage/MyPage";
import {
  acceptGuardian,
  getMyGuardians,
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
    relation: fallback?.relation ?? "친구",
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
  onDeleted,
  onNotifications,
}) {
  const [friend, setFriend] = useState(null);
  const [error, setError] = useState("");
  const [guardian, setGuardian] = useState(null);
  const [incoming, setIncoming] = useState(null);
  const [guardianBusy, setGuardianBusy] = useState(false);
  const [guardianMsg, setGuardianMsg] = useState("");
  const [deleting, setDeleting] = useState(false);

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

  const loadGuardian = useCallback(async () => {
    try {
      const [watching, mine] = await Promise.all([
        getWatching(userId),
        getMyGuardians(userId),
      ]);
      setGuardian(
        (watching ?? []).find((g) => g.userId === friendUserId) ?? null,
      );
      setIncoming(
        (mine ?? []).find((g) => g.guardianUserId === friendUserId) ?? null,
      );
    } catch (err) {
      console.error(err);
    }
  }, [userId, friendUserId]);

  // 이 친구에 대한 내 지킴이 상태 조회 (상세 조회와 분리: 실패해도 화면은 유지)
  useEffect(() => {
    setGuardian(null);
    setIncoming(null);
    setGuardianMsg("");
    loadGuardian();
  }, [loadGuardian]);

  const runGuardian = async (action, successMsg) => {
    if (guardianBusy) return;
    setGuardianBusy(true);
    setGuardianMsg("");
    try {
      await action();
      if (successMsg) setGuardianMsg(successMsg);
      await loadGuardian();
    } catch (err) {
      setGuardianMsg(err.message || "처리하지 못했어요.");
    } finally {
      setGuardianBusy(false);
    }
  };

  const pendingIncoming = incoming && !incoming.active; // 상대가 보낸 신청 대기
  const isMutual = fallback?.mutual === true;
  const needsMutual = !guardian && !incoming; // 새 신청만 상호 친구 필요
  const blocked = needsMutual && !isMutual;

  const guardianLabel = blocked
    ? "서로 친구일 때 신청할 수 있어요"
    : pendingIncoming
      ? "안부지킴이 신청 수락"
      : incoming?.active
        ? "나를 지키는 중 (누르면 해지)"
        : !guardian
          ? "안부지킴이 신청"
          : guardian.active
            ? "안부지킴이 해지"
            : "신청 취소 (수락 대기 중)";

  const handleGuardianClick = () => {
    const name = friend?.name ?? "이 친구";
    if (pendingIncoming) {
      if (
        window.confirm(
          `${name}님의 안부지킴이 신청을 수락하시겠습니까?\n수락하면 내 접속 여부를 알 수 있어요.`,
        )
      ) {
        runGuardian(
          () => acceptGuardian(userId, incoming.guardianSettingId),
          "안부지킴이를 수락했어요.",
        );
      }
      return;
    }
    if (incoming?.active) {
      if (window.confirm(`${name}님의 안부지킴이를 해지할까요?`)) {
        runGuardian(() => removeGuardian(userId, incoming.guardianSettingId));
      }
      return;
    }

    if (!guardian) {
      if (window.confirm("이 친구에게 안부지킴이를 신청할까요?")) {
        runGuardian(
          () => requestGuardian(userId, friendUserId, 3),
          "안부지킴이를 신청했어요. 상대가 수락하면 알림을 받을 수 있어요.",
        );
      }
      return;
    }
    if (window.confirm(`${guardianLabel}할까요?`)) {
      runGuardian(() => removeGuardian(userId, guardian.guardianSettingId));
    }
  };

  const handleDeleteFriend = async () => {
    if (deleting) return;
    if (
      !window.confirm(`${friend?.name ?? "이 친구"}님을 친구에서 삭제할까요?`)
    )
      return;
    setDeleting(true);
    setGuardianMsg("");
    try {
      await deleteFriend(userId, friendUserId);
      onDeleted?.();
    } catch (err) {
      setGuardianMsg(err.message || "친구를 삭제하지 못했어요.");
      setDeleting(false);
    }
  };

  const stateClass = pendingIncoming
    ? "alert"
    : guardian?.active || incoming?.active
      ? "on"
      : guardian
        ? "pending"
        : "";

  const guardianButton = (
    <button
      type="button"
      className={`round-button guardian-button ${stateClass}`}
      aria-label={guardianLabel}
      title={guardianLabel}
      disabled={guardianBusy || blocked}
      onClick={handleGuardianClick}
    >
      <Icon name="shield" size={20} />
    </button>
  );

  const deleteButton = (
    <button
      type="button"
      className="round-button"
      aria-label="친구 삭제"
      title="친구 삭제"
      disabled={deleting}
      onClick={handleDeleteFriend}
    >
      <Icon name="trash" size={20} />
    </button>
  );

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
        topBarActions={
          <>
            {deleteButton}
            {guardianButton}
          </>
        }
      />
      {guardianMsg && <p className="guardian-toast">{guardianMsg}</p>}
    </>
  );
}
