import { useEffect, useState } from "react";
import { TopBar } from "../shared/ui";
import { getFriendDetail } from "../api/friendApi";
import ProfileScreen from "../mypage/MyPage";

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
  }, [userId, friendUserId]);

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
    <ProfileScreen
      friend={friend}
      onBack={onBack}
      onNotifications={onNotifications}
    />
  );
}
