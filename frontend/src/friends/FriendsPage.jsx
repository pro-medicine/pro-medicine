import { useEffect, useState, useRef } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriends } from "../api/friendApi";
import "./friends.css";
import AddFriendModal from "./AddFriendModal";
import FollowRequestModal from "./FollowRequestsModal";
import FriendDetailPage from "./FriendDetailPage";

// 서버에 색 정보가 없으므로 순서대로 돌려 씀 (실제 쓰는 색 클래스명으로 수정)
const COLORS = ["mint", "blue", "yellow", "pink"];

// API 응답 → 기존 Friend 모양으로 변환
// 서버에 아직 없는 값(relation, status, streak)은 기본값
function toFriend(item, index) {
  return {
    ...item,
    id: item.followId,
    name: item.nickname ?? "",
    initial: item.nickname?.charAt(0) ?? "?",
    color: item.color ?? COLORS[index % COLORS.length],
    relation: item.relation ?? "친구",
    streak: item.streak ?? 0,
  };
}

export default function FriendsPage({ onNotifications }) {
  // TODO: 로그인 연동 후 실제 사용자 id
  const userId = 1;

  const [friends, setFriends] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedFriend, setSelectedFriend] = useState(null); // 추가
  const [addOpen, setAddOpen] = useState(false);
  const [requestsOpen, setRequestsOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const openerRef = useRef(null);

  const openModal = (setter) => (e) => {
    openerRef.current = e.currentTarget;
    setter(true);
  };

  useEffect(() => {
    let ignore = false;
    setError("");
    getFriends(userId)
      .then((data) => {
        if (!ignore) {
          setFriends((data ?? []).map(toFriend));
          setError("");
        }
      })
      .catch((err) => {
        console.error(err);
        if (!ignore)
          setError(err.message || "친구 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [userId, reloadKey]);

  const filtered = friends.filter((friend) => friend.name.includes(query));
  if (selectedFriend) {
    return (
      <FriendDetailPage
        userId={userId}
        friendUserId={selectedFriend.userId}
        fallback={selectedFriend}
        onBack={() => setSelectedFriend(null)}
        onNotifications={onNotifications}
      />
    );
  }
  return (
    <div className="screen">
      <TopBar
        title="친구 영양정보"
        subtitle="함께 챙기는 건강 습관"
        onNotifications={onNotifications}
      />

      <div className="search-box">
        <Icon name="search" size={19} />
        <input
          aria-label="친구 검색"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="친구 이름을 검색해보세요"
          value={query}
        />
      </div>

      <section className="friends-highlight">
        <div>
          <span className="section-label">TOGETHER</span>
          <h2>친구 {friends.length}명이 함께하고 있어요</h2>
          <p>서로의 건강 루틴을 보며 꾸준한 습관을 만들어요.</p>
        </div>
        <div className="stacked-avatars">
          {friends.slice(0, 3).map((friend) => (
            <span className={`avatar ${friend.color}`} key={friend.id}>
              {friend.initial}
            </span>
          ))}
          {friends.length > 3 && (
            <span className="avatar more">+{friends.length - 3}</span>
          )}
        </div>
      </section>

      <section className="section-block friends-section">
        <div className="section-title-row">
          <div>
            <span className="section-label">MY FRIENDS</span>
            <h2>친구들의 오늘</h2>
          </div>
          <div className="friend-header-actions">
            <button
              className="add-friend-button"
              onClick={openModal(setRequestsOpen)}
            >
              친구 신청
            </button>
            <button
              className="add-friend-button"
              onClick={openModal(setAddOpen)}
            >
              <Icon name="plus" size={16} />
              친구 추가
            </button>
          </div>
        </div>

        <div className="friend-list">
          {loading && <p>친구 목록을 불러오는 중...</p>}
          {!loading && error && <p>{error}</p>}
          {!loading && !error && filtered.length === 0 && (
            <p>
              {friends.length === 0
                ? "등록된 친구가 없습니다."
                : "검색 결과가 없습니다."}
            </p>
          )}

          {!loading &&
            !error &&
            filtered.map((friend) => (
              <button
                className="friend-row"
                key={friend.id}
                onClick={() => setSelectedFriend(friend)}
              >
                <span className={`avatar large ${friend.color}`}>
                  {friend.initial}
                </span>
                <span className="friend-copy">
                  <span>
                    <strong>{friend.name}</strong>
                    <small>{friend.relation}</small>
                  </span>
                </span>
                <span className="friend-status">{friend.status}</span>
                <span className="friend-meta">
                  {friend.streak === 0 ? (
                    <span className="friend-encourage">
                      친구를 응원해보세요 <Icon name="chevron" size={18} />
                    </span>
                  ) : (
                    <span className="friend-streak">
                      <span>
                        <b>{friend.streak}</b>일째
                      </span>
                      <Icon name="chevron" size={18} />
                    </span>
                  )}
                </span>
              </button>
            ))}
        </div>
      </section>
      {requestsOpen && (
        <FollowRequestModal
          userId={userId}
          onClose={() => setRequestsOpen(false)}
          onAccepted={() => setReloadKey((k) => k + 1)}
          openerRef={openerRef}
        />
      )}
      {addOpen && (
        <AddFriendModal
          userId={userId}
          onClose={() => setAddOpen(false)}
          openerRef={openerRef}
        />
      )}
    </div>
  );
}
