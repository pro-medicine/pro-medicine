import { useEffect, useState, useRef } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriends } from "../api/friendApi";
import "./friends.css";
import AddFriendModal from "./AddFriendModal";
import FollowRequestModal from "./FollowRequestModal";
import FriendDetailPage from "./FriendDetailPage";
import { getWatching } from "../api/guardianApi";
import {
  getFollowRequests,
  getFollowers,
  requestFollow,
} from "../api/followApi";
import { fetchIntakeStats, fetchUser } from "../api/mypageApi";
import FriendRanking from "./FriendRanking";
import { RequestCard, GuardianCard, FriendTipCard } from "./FriendSideCards";

// 서버에 색 정보가 없으므로 순서대로 돌려 씀 (실제 쓰는 색 클래스명으로 수정)
const COLORS = ["mint", "blue", "yellow", "pink"];

// API 응답 → 기존 Friend 모양으로 변환
// 서버에 아직 없는 값(relation, status, streak)은 기본값
function toFriend(item, index) {
  return {
    ...item,
    id: item.followId,
    key: `friend-${item.followId}`,
    name: item.nickname ?? "",
    initial: item.nickname?.charAt(0) ?? "?",
    color: item.color ?? COLORS[index % COLORS.length],
    relation: item.mutual ? "친구" : "팔로잉",
    streak: item.streakDays ?? 0,
  };
}

// openFriendUserId: 알림의 "친구 보기"로 들어오면 그 친구 상세부터 보여줌
export default function FriendsPage({
  userId,
  onNotifications,
  openFriendUserId = null,
  onSelectFriend,
  onCloseFriend,
  onRegisterBack,
  onDismissPanel,
  restoreChild = null,
}) {
  const [friends, setFriends] = useState([]);
  const [followers, setFollowers] = useState([]); // 나를 팔로우하는 사람 (맞팔로우 대상)
  const [busyId, setBusyId] = useState(null); // 맞팔로우 신청 중인 userId
  const [toast, setToast] = useState(""); // 맞팔로우 결과 메시지
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const selectedFriend = openFriendUserId ? { userId: openFriendUserId } : null;
  const [addOpen, setAddOpen] = useState(false);
  const [requestsOpen, setRequestsOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [watchingIds, setWatchingIds] = useState(new Set());
  const [watching, setWatching] = useState([]); // 내가 지키는 친구 (안부지킴이 카드)
  const [requests, setRequests] = useState(null); // 받은 친구 신청, null 이면 불러오는 중
  const [me, setMe] = useState(null); // 랭킹에 넣을 "나"
  const [tab, setTab] = useState("all"); // 전체 / 친구 / 팔로잉 / 팔로워
  const [requestTab, setRequestTab] = useState("received");
  const openerRef = useRef(null);

  useEffect(() => {
    if (restoreChild === "add") setAddOpen(true);
    else if (restoreChild?.startsWith("requests:")) {
      setRequestTab(restoreChild.slice("requests:".length));
      setRequestsOpen(true);
    }
  }, [restoreChild]);

  useEffect(() => {
    if (!onRegisterBack) return undefined;
    const close = requestsOpen
      ? () => {
          setRequestsOpen(false);
          setReloadKey((k) => k + 1);
        }
      : addOpen ? () => setAddOpen(false) : null;
    const childId = requestsOpen ? `requests:${requestTab}` : addOpen ? "add" : null;
    onRegisterBack("friends", close, childId);
    return () => onRegisterBack("friends", null);
  }, [requestsOpen, requestTab, addOpen, onRegisterBack]);

  const dismissPanel = (close) => {
    onDismissPanel?.("friends");
    close();
  };

  const openModal = (setter) => (e) => {
    openerRef.current = e.currentTarget;
    setter(true);
  };
  // 친구 신청 모달: 받은 / 보낸 탭을 골라서 연다
  const openRequests = (which, e) => {
    openerRef.current = e.currentTarget;
    setRequestTab(which);
    setRequestsOpen(true);
  };

  // 처음 불러올 때만 "불러오는 중..."을 보여줌 (친구 신청 수락·모달 닫기 등으로 다시 불러올 때는 기존 목록을 유지해서 깜빡이지 않게)
  const loadedRef = useRef(false);

  useEffect(() => {
    let ignore = false;
    if (!loadedRef.current) setLoading(true);
    getWatching(userId)
      .then((data) => {
        if (ignore) return;
        setWatching(data ?? []);
        setWatchingIds(
          new Set((data ?? []).filter((g) => g.active).map((g) => g.userId)),
        );
      })
      .catch(() => {});

    getFollowRequests("received")
      .then((data) => {
        if (!ignore) setRequests(data ?? []);
      })
      .catch(() => {
        if (!ignore) setRequests([]);
      });

    // 나를 팔로우하는 사람 (내가 아직 수락된 팔로우가 없는 사람)
    getFollowers(userId)
      .then((data) => {
        if (!ignore) setFollowers(data ?? []);
      })
      .catch(() => {});

    // 랭킹의 "나": 내 연속 복용일 + 닉네임 (못 불러오면 나를 빼고 친구끼리만 보여줌)
    Promise.all([fetchIntakeStats(), fetchUser().catch(() => null)])
      .then(([stats, user]) => {
        if (ignore) return;
        setMe({
          key: "me",
          isMe: true,
          name: user?.nickname ?? "나",
          streak: stats?.streakDays ?? 0,
        });
      })
      .catch(() => {
        if (!ignore) setMe(null);
      });

    getFriends()
      .then((data) => {
        if (!ignore) {
          setFriends((data ?? []).map(toFriend));
          setError("");
          loadedRef.current = true;
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

  // 토스트는 3초 뒤 사라짐
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 3000);
    return () => clearTimeout(timer);
  }, [toast]);

  // 맞팔로우: 기존 팔로우 신청(POST /follows)을 닉네임으로 재사용
  const handleFollowBack = async (f) => {
    if (busyId) return;
    setBusyId(f.userId);
    try {
      await requestFollow(f.nickname);
      setToast(`${f.nickname}님에게 맞팔로우를 신청했어요.`);
      setReloadKey((k) => k + 1);
    } catch (err) {
      console.error(err);
      setToast(err.message || "맞팔로우를 신청하지 못했어요.");
    } finally {
      setBusyId(null);
    }
  };

  const friendsById = new Map(friends.map((f) => [f.userId, f]));
  if (selectedFriend) {
    return (
      <FriendDetailPage
        userId={userId}
        friendUserId={selectedFriend.userId}
        // 알림에서 바로 들어오면 { userId }만 있어서, 목록을 불러온 뒤에는 목록 정보(서로 친구 여부 등)를 사용
        fallback={friendsById.get(selectedFriend.userId) ?? selectedFriend}
        onBack={() => {
          onCloseFriend();
          setReloadKey((k) => k + 1);
        }}
        onDeleted={() => {
          onCloseFriend();
          setReloadKey((k) => k + 1);
        }}
        onNotifications={onNotifications}
      />
    );
  }
  return (
    <div className="screen friends-page">
      {/* 머리: 제목(왼쪽) + 검색·친구 추가(오른쪽), 친구 신청은 오른쪽 카드로 이동 */}
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
            className="btn btn-primary"
            onClick={openModal(setAddOpen)}
          >
            <Icon name="plus" size={16} />
            친구 추가
          </button>
        </div>
      </div>

      {/* 2단: 왼쪽 연속 복용 랭킹 / 오른쪽 친구 신청·안부지킴이·안내 (#67 친구 시안 C+) */}
      <div className="fr-layout">
        <FriendRanking
          friends={friends}
          followers={followers}
          onFollowBack={handleFollowBack}
          busyId={busyId}
          me={me}
          watchingIds={watchingIds}
          query={query}
          tab={tab}
          onTabChange={setTab}
          loading={loading}
          error={error}
          onSelect={onSelectFriend}
        />
        <aside className="fr-side">
          <RequestCard
            userId={userId}
            requests={requests}
            onOpenModal={openRequests}
            onChanged={() => setReloadKey((k) => k + 1)}
          />
          <GuardianCard
            watching={watching}
            friendsById={friendsById}
            onSelect={onSelectFriend}
          />
          <FriendTipCard />
        </aside>
      </div>

      {toast && <p className="guardian-toast">{toast}</p>}

      {requestsOpen && (
        <FollowRequestModal
          userId={userId}
          initialTab={requestTab}
          onTabChange={setRequestTab}
          onClose={() => dismissPanel(() => {
            setRequestsOpen(false);
            setReloadKey((k) => k + 1); // 모달에서 수락·취소한 내용을 카드에도 반영
          })}
          onAccepted={() => setReloadKey((k) => k + 1)}
          openerRef={openerRef}
        />
      )}
      {addOpen && (
        <AddFriendModal
          userId={userId}
          onClose={() => dismissPanel(() => setAddOpen(false))}
          openerRef={openerRef}
        />
      )}
    </div>
  );
}
