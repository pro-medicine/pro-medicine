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
import ConfirmDialog from "./ConfirmDialog";

function toFriendDetail(data, fallback) {
  return {
    ...data,
    id: data.userId,
    name: data.nickname ?? "",
    initial: data.nickname?.charAt(0) ?? "?",
    color: fallback?.color ?? "mint",
    relation: fallback?.relation ?? "친구",
    mutual: fallback?.mutual, // 서로 친구면 초록 칩
    // 이름 아래 한 줄: 연속 복용일 기준
    status:
      (data.streak ?? 0) > 0
        ? `${data.streak}일째 꾸준히 챙기는 중`
        : "오늘부터 다시 시작해요",
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
  // 확인 / 취소 화면: { title, message, confirmLabel, danger, onConfirm }
  const [confirm, setConfirm] = useState(null);
  const askConfirm = (options) => {
    // 다른 창(설정 모달 등) 안의 버튼에서 열면 그 버튼은 곧 사라지므로, 처음 창을 연 버튼을 그대로 복원 대상으로 둔다
    const active = document.activeElement;
    if (active instanceof HTMLElement && !active.closest('[role="dialog"], [role="alertdialog"]')) {
      openerRef.current = active;
    }
    setConfirm(options);
  };
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

  // 설정 모달의 "안부지킴이 해지" → 설정 모달을 닫고 확인 화면
  const handleCancelGuardian = () => {
    setDaysOpen(false);
    askConfirm({
      title: "안부지킴이를 해제할까요?",
      message: `해제하면 ${friend?.name ?? "이 친구"}님이 오래 접속하지 않아도 알림을 받지 않아요.`,
      confirmLabel: "해제",
      danger: true,
      onConfirm: () =>
        runGuardian(() => removeGuardian(userId, guardian.guardianSettingId), "안부지킴이를 해제했어요."),
    });
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
      askConfirm({
        title: "안부지킴이 신청을 수락할까요?",
        message: `수락하면 ${name}님이 내 접속 여부를 알 수 있어요.`,
        confirmLabel: "수락",
        onConfirm: () =>
          runGuardian(() => acceptGuardian(userId, incoming.guardianSettingId), "안부지킴이를 수락했어요."),
      });
      return;
    }

    if (incoming?.active) {
      askConfirm({
        title: "안부지킴이를 해제할까요?",
        message: `해제하면 ${name}님이 더 이상 내 접속 여부를 알 수 없어요.`,
        confirmLabel: "해제",
        danger: true,
        onConfirm: () =>
          runGuardian(() => removeGuardian(userId, incoming.guardianSettingId), "안부지킴이를 해제했어요."),
      });
      return;
    }

    if (!guardian) {
      askConfirm({
        title: "안부지킴이를 신청할까요?",
        message: `${name}님이 수락하면, 3일 이상 접속하지 않을 때 알림을 받아요.`,
        confirmLabel: "신청",
        onConfirm: () =>
          runGuardian(
            () => requestGuardian(userId, friendUserId, 3),
            "안부지킴이를 신청했어요. 상대가 수락하면 알림을 받을 수 있어요.",
          ),
      });
      return;
    }

    if (!guardian.active) {
      askConfirm({
        title: "안부지킴이 신청을 취소할까요?",
        message: `${name}님에게 보낸 안부지킴이 신청을 취소해요.`,
        confirmLabel: "신청 취소",
        danger: true,
        onConfirm: () => runGuardian(() => removeGuardian(userId, guardian.guardianSettingId)),
      });
      return;
    }

    // 지키는 중 → 설정 모달
    openerRef.current = document.activeElement; // 닫을 때 버튼으로 복원
    setDaysOpen(true);
  };

  const handleDeleteFriend = () => {
    if (deleting) return;
    askConfirm({
      title: "친구를 삭제할까요?",
      message: `${friend?.name ?? "이 친구"}님을 친구 목록에서 삭제해요. 다시 친구가 되려면 친구 신청을 새로 보내야 해요.`,
      confirmLabel: "삭제",
      danger: true,
      onConfirm: deleteFriendNow,
    });
  };

  const deleteFriendNow = async () => {
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
            {deleteButton}
            {guardianButton}
          </>
        }
      />
      {guardianMsg && <p className="guardian-toast">{guardianMsg}</p>}
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          message={confirm.message}
          confirmLabel={confirm.confirmLabel}
          danger={confirm.danger}
          busy={guardianBusy || deleting}
          openerRef={openerRef}
          onCancel={() => setConfirm(null)}
          onConfirm={async () => {
            await confirm.onConfirm();
            setConfirm(null);
          }}
        />
      )}
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
