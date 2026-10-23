import { useEffect, useState } from "react";
import { Icon, TopBar } from "../shared/ui";
import { getFriends } from "../api/friendApi";
import "./friends.css";

export default function FriendsScreen({ onSelect, onNotifications }) {
  // 로그인 기능이 아직 없다면 임시로 테스트
  const userId = 1;

  const [friends, setFriends] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getFriends(userId)
      .then((data) => {
        setFriends(data ?? []);
      })
      .catch((err) => {
        console.error(err);
        setError(err.message || "친구 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const filtered = friends.filter((friend) =>
    friend.nickname.toLowerCase().includes(query.toLowerCase()),
  );

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
            <span className="avatar mint" key={friend.followId}>
              {friend.nickname?.charAt(0) ?? "?"}
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

          <button
            className="add-friend-button"
            onClick={() => {
              // TODO: 친구 추가 기능 연결
            }}
          >
            <Icon name="plus" size={16} />
            친구 추가
          </button>
        </div>

        <div className="friend-list">
          {loading ? (
            <p>친구 목록을 불러오는 중...</p>
          ) : error ? (
            <p>{error}</p>
          ) : filtered.length === 0 ? (
            <p>등록된 친구가 없습니다.</p>
          ) : (
            filtered.map((friend) => (
              <button
                className="friend-row"
                key={friend.followId}
                onClick={() => onSelect(friend)}
              >
                <span className="avatar large mint">
                  {friend.nickname.charAt(0)}
                </span>

                <span className="friend-copy">
                  <span>
                    <strong>{friend.nickname}</strong>
                  </span>
                  <small>친구 ID: {friend.userId}</small>
                </span>

                <Icon name="chevron" size={18} />
              </button>
            ))
          )}
        </div>
      </section>
    </div>
  );
}
