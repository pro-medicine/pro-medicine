import { useEffect, useState, useRef } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriends } from "../api/friendApi";
import "./friends.css";
import AddFriendModal from "./AddFriendModal";
import FollowRequestModal from "./FollowRequestModal";
import FriendDetailPage from "./FriendDetailPage";
import { getWatching } from "../api/guardianApi";

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
    relation: item.mutual ? "친구" : "팔로우",
    streak: item.streakDays ?? 0,
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
  const [watchingIds, setWatchingIds] = useState(new Set());
  const openerRef = useRef(null);

  const openModal = (setter) => (e) => {
    openerRef.current = e.currentTarget;
    setter(true);
  };

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError("");
    setWatchingIds(new Set());
    getWatching(userId)
      .then((data) => {
        if (ignore) return;
        setWatchingIds(
          new Set((data ?? []).filter((g) => g.active).map((g) => g.userId)),
        );
      })
      .catch(() => {});

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
        onBack={() => {
          setSelectedFriend(null);
          setReloadKey((k) => k + 1);
        }}
        onDeleted={() => {
          setSelectedFriend(null);
          setReloadKey((k) => k + 1);
        }}
        onNotifications={onNotifications}
      />
    );
  }
  return (
    <div className="screen friends-page">
      {/* 머리: 제목(왼쪽) + 검색·친구 신청·친구 추가(오른쪽) */}
      <div className="friends-head">
        <TopBar
          title="친구 영양정보"
          subtitle="함께 챙기는 건강 습관"
          description="서로의 건강 루틴을 보며 꾸준한 습관을 만들어요."
          onNotifications={onNotifications}
        />
        <div className="friends-toolbar">
          <div className="search-box">
            <Icon name="search" size={17} />
            <input
              aria-label="친구 검색"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="친구 이름을 검색해보세요"
              value={query}
            />
          </div>
          <button
            type="button"
            className="btn btn-outline"
            onClick={openModal(setRequestsOpen)}
          >
            친구 신청
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={openModal(setAddOpen)}
          >
            <Icon name="plus" size={16} />
            친구 추가
          </button>
        </div>
      </div>

      {/* 친구 목록 표: 친구 · 관계 · 연속 복용 · 안부지킴이 */}
      <section className="friend-table" aria-label="친구 목록">
        <div className="friend-table-head" aria-hidden="true">
          <span>친구 {friends.length}명</span>
          <span>관계</span>
          <span>연속 복용</span>
          <span>안부지킴이</span>
          <span />
        </div>

        {loading && <p className="friend-table-state">친구 목록을 불러오는 중...</p>}
        {!loading && error && <p className="friend-table-state">{error}</p>}
        {!loading && !error && filtered.length === 0 && (
          <p className="friend-table-state">
            {friends.length === 0
              ? "등록된 친구가 없습니다."
              : "검색 결과가 없습니다."}
          </p>
        )}

        {!loading &&
          !error &&
          filtered.map((friend) => (
            <button
              type="button"
              className="friend-row"
              key={friend.id}
              onClick={() => setSelectedFriend(friend)}
            >
              <span className="friend-name">
                <span className="avatar">{friend.initial}</span>
                <strong>{friend.name}</strong>
              </span>
              <span className="friend-relation">
                <span className={`chip ${friend.mutual ? "green" : ""}`}>
                  {friend.relation}
                </span>
              </span>
              <span className="friend-streak">
                {friend.streak === 0 ? (
                  <span className="friend-encourage">오늘 응원이 필요해요</span>
                ) : (
                  <b>{friend.streak}일 연속</b>
                )}
              </span>
              <span className="friend-guardian">
                {watchingIds.has(friend.userId) ? (
                  <>
                    <Icon name="shield" size={15} />
                    안부지킴이
                  </>
                ) : (
                  <span className="friend-none">–</span>
                )}
              </span>
              <span className="friend-chevron" aria-hidden="true">
                <Icon name="chevron" size={18} />
              </span>
            </button>
          ))}
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
