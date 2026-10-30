// 연속 복용 랭킹 카드 (#67 친구 시안 C+)
// 위: 1·2·3위 시상대 / 아래: 전체 순위 (나 포함, 1위 대비 주황 막대)
import { Icon } from "../shared/ui";

const TABS = [
  { id: "all", label: "전체" },
  { id: "friend", label: "친구" },
  { id: "follow", label: "팔로우" },
];

function Flame({ size = 14 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3c1 3.5 5 5.5 5 10a5 5 0 0 1-10 0c0-2 1-3.5 2-4.5 0 2 1 3 2 3 0-3-1-5.5 1-8.5Z" />
    </svg>
  );
}

export function FriendAvatar({ person, size = "md" }) {
  return (
    <span className={`fr-avatar is-${size} ${person.isMe ? "is-me" : `is-${person.color}`}`} aria-hidden="true">
      {person.isMe ? "나" : person.initial}
    </span>
  );
}

// 같은 일수면 같은 순위 (1, 2, 2, 4 …)
function withRanks(list) {
  let prevDays = null;
  let prevRank = 0;
  return list.map((p, i) => {
    const rank = p.streak === prevDays ? prevRank : i + 1;
    prevDays = p.streak;
    prevRank = rank;
    return { ...p, rank };
  });
}

function Podium({ top3 }) {
  // 2위 · 1위 · 3위 순서로 배치
  const order = [top3[1], top3[0], top3[2]].filter(Boolean);
  return (
    <div className="fr-podium">
      {order.map((p) => {
        const place = top3.indexOf(p) + 1; // 자리(1~3), 공동 순위여도 자리는 고정
        return (
          <div key={p.key} className={`fr-podium-item is-place-${place}`}>
            <span className="fr-podium-face">
              {place === 1 && (
                <svg className="fr-crown" width="26" height="18" viewBox="0 0 26 18" aria-hidden="true">
                  <path d="M2 16 4 4l5.5 5L13 1l3.5 8L22 4l2 12Z" />
                </svg>
              )}
              <FriendAvatar person={p} size={place === 1 ? "xl" : "lg"} />
              <span className="fr-medal">{p.rank}</span>
            </span>
            <strong>{p.isMe ? "나" : p.name}</strong>
            <span className="fr-days">
              <Flame />
              {p.streak}일
            </span>
            <span className="fr-podium-stand" aria-hidden="true" />
          </div>
        );
      })}
    </div>
  );
}

export default function FriendRanking({
  friends,
  me,
  watchingIds,
  query,
  tab,
  onTabChange,
  loading,
  error,
  onSelect,
}) {
  // 나 + 친구 전체를 연속 일수 순으로
  const everyone = withRanks(
    [...(me ? [me] : []), ...friends].sort(
      (a, b) => b.streak - a.streak || Number(b.isMe ?? false) - Number(a.isMe ?? false),
    ),
  );
  const topDays = everyone[0]?.streak ?? 0;
  const myEntry = everyone.find((p) => p.isMe);
  const counts = {
    all: friends.length,
    friend: friends.filter((f) => f.mutual).length,
    follow: friends.filter((f) => !f.mutual).length,
  };

  // 목록: 탭·검색 적용 (나는 "전체" 탭에서 검색어가 없을 때만)
  const visible = everyone.filter((p) => {
    if (p.isMe) return tab === "all" && !query;
    if (tab === "friend" && !p.mutual) return false;
    if (tab === "follow" && p.mutual) return false;
    return p.name.includes(query);
  });

  let summary = "친구와 함께 연속 복용 기록을 비교해 보세요";
  if (friends.length > 0 && myEntry) {
    summary =
      myEntry.rank === 1
        ? myEntry.streak > 0
          ? "지금 내가 1위예요! 이대로 이어가 볼까요?"
          : "오늘부터 함께 기록을 시작해 보세요"
        : (
            <>
              나는 친구들 중 <b>{myEntry.rank}위</b>예요 · 1위까지{" "}
              <b className="is-streak">{topDays - myEntry.streak}일</b>
            </>
          );
  }

  const showPodium = !loading && !error && topDays > 0 && everyone.length >= 2;

  return (
    <section className="fr-card fr-rank" aria-label="연속 복용 랭킹">
      <div className="fr-rank-head">
        <div>
          <h2>연속 복용 랭킹</h2>
          <p>{summary}</p>
        </div>
        <div className="fr-tabs" role="tablist" aria-label="친구 종류">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              className={tab === t.id ? "active" : ""}
              onClick={() => onTabChange(t.id)}
            >
              {t.label} {counts[t.id]}
            </button>
          ))}
        </div>
      </div>

      {showPodium && <Podium top3={everyone.slice(0, 3)} />}

      <div className={`fr-rank-list${showPodium ? " has-podium" : ""}`}>
        {loading && <p className="fr-state">친구 목록을 불러오는 중...</p>}
        {!loading && error && <p className="fr-state">{error}</p>}
        {!loading && !error && friends.length === 0 && (
          <p className="fr-state">아직 친구가 없어요. 친구를 추가하고 함께 기록을 이어가 보세요.</p>
        )}
        {!loading && !error && friends.length > 0 && visible.filter((p) => !p.isMe).length === 0 && (
          <p className="fr-state">{query ? "검색 결과가 없어요." : "해당하는 친구가 없어요."}</p>
        )}
        {!loading &&
          !error &&
          friends.length > 0 &&
          visible.map((p) => {
            const percent = topDays ? Math.round((p.streak / topDays) * 100) : 0;
            const content = (
              <>
                <span className={`fr-rank-no${p.rank <= 3 ? " is-top" : ""}`}>{p.rank}</span>
                <FriendAvatar person={p} />
                <span className="fr-rank-name">
                  <strong>{p.name}</strong>
                  {p.isMe ? (
                    <span className="fr-chip is-me">나</span>
                  ) : (
                    <span className={`fr-chip ${p.mutual ? "is-friend" : "is-follow"}`}>
                      {p.mutual ? "친구" : "팔로우"}
                    </span>
                  )}
                  {watchingIds.has(p.userId) && (
                    <span className="fr-guard-icon" title="안부지킴이" aria-label="안부지킴이">
                      <Icon name="shield" size={15} />
                    </span>
                  )}
                </span>
                {p.streak > 0 ? (
                  <span className="fr-rank-bar">
                    <span className="fr-bar-track" aria-hidden="true">
                      <span style={{ width: `${percent}%` }} />
                    </span>
                    <b>{p.streak}일</b>
                  </span>
                ) : (
                  <span className="fr-rank-bar is-empty">새로 시작하는 중</span>
                )}
                <span className="fr-rank-chevron" aria-hidden="true">
                  {!p.isMe && <Icon name="chevron" size={18} />}
                </span>
              </>
            );
            return p.isMe ? (
              <div key={p.key} className="fr-rank-row is-me">
                {content}
              </div>
            ) : (
              <button
                key={p.key}
                type="button"
                className="fr-rank-row"
                onClick={() => onSelect(p)}
                aria-label={`${p.rank}위 ${p.name}, ${p.streak}일 연속 복용, 상세 보기`}
              >
                {content}
              </button>
            );
          })}
      </div>
    </section>
  );
}
