import { useState } from "react";
import { friends, Icon, TopBar, type Friend } from "../shared/ui";

export default function FriendsScreen({
  onSelect,
  onNotifications,
}: {
  onSelect: (friend: Friend) => void;
  onNotifications: () => void;
}) {
  const [query, setQuery] = useState("");
  const filtered = friends.filter((friend) => friend.name.includes(query));

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
          <h2>친구 4명이 함께하고 있어요</h2>
          <p>서로의 건강 루틴을 보며 꾸준한 습관을 만들어요.</p>
        </div>
        <div className="stacked-avatars">
          {friends.slice(0, 3).map((friend) => (
            <span className={`avatar ${friend.color}`} key={friend.id}>
              {friend.initial}
            </span>
          ))}
          <span className="avatar more">+1</span>
        </div>
      </section>

      <section className="section-block friends-section">
        <div className="section-title-row">
          <div>
            <span className="section-label">MY FRIENDS</span>
            <h2>친구들의 오늘</h2>
          </div>
          <button className="add-friend-button">
            <Icon name="plus" size={16} />
            친구 추가
          </button>
        </div>

        <div className="friend-list">
          {filtered.map((friend) => (
            <button className="friend-row" key={friend.id} onClick={() => onSelect(friend)}>
              <span className={`avatar large ${friend.color}`}>{friend.initial}</span>
              <span className="friend-copy">
                <span>
                  <strong>{friend.name}</strong>
                  <small>{friend.relation}</small>
                </span>
                <span className="friend-status">
                  <i className={friend.completed === friend.supplements.length ? "complete" : ""} />
                  {friend.status}
                </span>
              </span>
              <span className="friend-meta">
                <span><b>{friend.streak}</b>일째</span>
                <small>{friend.supplements.length}개 복용 중</small>
              </span>
              <Icon name="chevron" size={18} />
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
