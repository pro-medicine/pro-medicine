import { useCallback, useEffect, useState, useRef } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriendDetail, deleteFriend } from "../api/friendApi";
import ProfileScreen from "../mypage/MyPage";
import {
  acceptGuardian,
  getMyGuardians,
  getWatching,
  requestGuardian,
  removeGuardian,
  changeGuardianDays,
} from "../api/guardianApi";
import GuardianDaysModal from "../friends/GuardianModal";

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
  const [daysOpen, setDaysOpen] = useState(false);
  const openerRef = useRef(null);

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

  useEffect(() => {
    if (!guardianMsg) return;
    const timer = setTimeout(() => setGuardianMsg(""), 3000);
    return () => clearTimeout(timer);
  }, [guardianMsg]);

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

  const handleChangeDays = async (days) => {
    await runGuardian(
      () => changeGuardianDays(userId, guardian.guardianSettingId, days),
      `${days}일 이상 접속하지 않으면 알림을 받아요.`,
    );
    setDaysOpen(false);
  };

  const handleCancelGuardian = async () => {
    if (!window.confirm("안부지킴이를 취소할까요?")) return;
    await runGuardian(
      () => removeGuardian(userId, guardian.guardianSettingId),
      "안부지킴이를 취소했어요.",
    );
    setDaysOpen(false);
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
            ? "안부지킴이 설정 (일수 변경 / 취소)"
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

    if (!guardian.active) {
      if (window.confirm("안부지킴이 신청을 취소할까요?")) {
        runGuardian(() => removeGuardian(userId, guardian.guardianSettingId));
      }
      return;
    }

    // 지키는 중 → 설정 모달
    openerRef.current = document.activeElement; // 닫을 때 버튼으로 복원
    setDaysOpen(true);
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

  // 버튼에 보이는 짧은 문구 (자세한 설명은 title·aria-label의 guardianLabel)
  const guardianText = pendingIncoming
    ? "안부지킴이 수락"
    : incoming?.active
      ? "나를 지키는 중"
      : !guardian
        ? "안부지킴이 신청"
        : guardian.active
          ? "안부지킴이 설정"
          : "신청 대기 중";

  const guardianButton = (
    <button
      type="button"
      className={`btn btn-outline guardian-button ${stateClass}`}
      aria-label={guardianLabel}
      title={guardianLabel}
      disabled={guardianBusy || blocked}
      onClick={handleGuardianClick}
    >
      <Icon name="shield" size={17} />
      {guardianText}
    </button>
  );

  const deleteButton = (
    <button
      type="button"
      className="btn-text danger friend-delete"
      disabled={deleting}
      onClick={handleDeleteFriend}
    >
      친구 삭제
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
        viewerId={userId}
        onBack={onBack}
        onNotifications={onNotifications}
        topBarActions={
          <>
            {guardianButton}
            {deleteButton}
          </>
        }
      />
      {guardianMsg && <p className="guardian-toast">{guardianMsg}</p>}
      {daysOpen && guardian?.active && (
        <GuardianDaysModal
          name={friend.name}
          currentDays={guardian.inactivityDays}
          busy={guardianBusy}
          openerRef={openerRef}
          onClose={() => setDaysOpen(false)}
          onChange={handleChangeDays}
          onCancelGuardian={handleCancelGuardian}
        />
      )}
    </>
  );
}
