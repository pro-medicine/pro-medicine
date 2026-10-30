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

// 연속 기록이 없는 자리: 점선 빈 자리
function EmptySeat({ place }) {
  return (
    <div className={`fr-podium-item is-place-${place} is-empty`}>
      <span className="fr-podium-face">
        <span className={`fr-avatar is-${place === 1 ? "xl" : "lg"} is-empty`} aria-hidden="true">
          {place}
        </span>
      </span>
      <strong>{place === 1 ? "1위 자리" : `${place}위 자리`}</strong>
      <span className="fr-days is-empty">비어 있어요</span>
      <span className="fr-podium-stand" aria-hidden="true" />
    </div>
  );
}

function Podium({ top3 }) {
  // 2위 · 1위 · 3위 순서로 배치, 연속 기록(1일 이상)이 없는 자리는 빈 자리로
  const seats = [0, 1, 2].map((i) => (top3[i]?.streak > 0 ? top3[i] : null));
  const empty = seats.every((p) => p === null);
  return (
    <div className={`fr-podium${empty ? " is-all-empty" : ""}`}>
      {[1, 0, 2].map((i) => {
        const p = seats[i];
        const place = i + 1; // 자리(1~3), 공동 순위여도 자리는 고정
        if (!p) return <EmptySeat key={`empty-${place}`} place={place} />;
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
      {empty && <p className="fr-podium-hint">오늘 약속을 모두 지키면 내일 첫 1위가 될 수 있어요</p>}
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
          : "아직 연속 기록이 있는 사람이 없어요 · 오늘 먼저 시작해 보세요"
        : myEntry.streak === 0
          ? (
              <>
                {everyone[0].name}님이 <b className="is-streak">{topDays}일</b>째 1위예요 · 오늘부터 나도 시작해 보세요
              </>
            )
          : (
            <>
              나는 친구들 중 <b>{myEntry.rank}위</b>예요 · 1위까지{" "}
              <b className="is-streak">{topDays - myEntry.streak}일</b>
            </>
          );
  }

  const showPodium = !loading && !error && friends.length > 0;

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
                {/* 연속 기록이 없으면 순위 대신 "–" (모두 0일이면 전부 1위로 보이는 문제 방지) */}
                <span className={`fr-rank-no${p.streak > 0 && p.rank <= 3 ? " is-top" : ""}`}>
                  {p.streak > 0 ? p.rank : "–"}
                </span>
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
