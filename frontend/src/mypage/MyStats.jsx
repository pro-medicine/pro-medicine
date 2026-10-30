// 마이페이지 통계 카드 3개 (#67 마이페이지 시안 B)
// 연속 복용(최고 기록 배지) / 이번 주 달성률(요일 막대) / 이번 달 달성률(주차별 꺾은선)
// stats: GET /api/v1/intakes/stats 응답, 못 불러오면 null
const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];

// 한국 시간 기준 오늘 "YYYY-MM-DD"
function kstTodayKey() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
}

function BigNumber({ value, unit }) {
  return (
    <strong className="my-stat-big">
      {value}
      <small>{unit}</small>
    </strong>
  );
}

function Flame() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3c1 3.5 5 5.5 5 10a5 5 0 0 1-10 0c0-2 1-3.5 2-4.5 0 2 1 3 2 3 0-3-1-5.5 1-8.5Z" />
    </svg>
  );
}

// 1. 연속 복용 + 최고 기록
function StreakCard({ streak, best }) {
  let message = "오늘 모두 챙기면\n연속 기록이 시작돼요";
  if (streak > 0 && streak < best) message = `${best - streak}일만 더 이어가면\n최고 기록을 넘어요`;
  else if (streak > 0) message = "지금이 최고 기록이에요!\n계속 이어가 볼까요?";

  return (
    <section className="my-stat-card">
      <span className="my-stat-label">연속 복용</span>
      <div className="my-stat-streak">
        <BigNumber value={streak ?? "-"} unit="일" />
        {best > 0 && (
          <span className="my-stat-best">
            <Flame />
            최고 {best}일
          </span>
        )}
      </div>
      {streak != null && <p className="my-stat-message">{message}</p>}
    </section>
  );
}

// 2. 이번 주 달성률 + 요일 막대 (모두 복용 = 진한 초록, 일부 = 연한 초록, 오늘 이후 = 점선)
function WeekCard({ rate, days, todayKey }) {
  return (
    <section className="my-stat-card">
      <div className="my-stat-top">
        <span className="my-stat-label">이번 주 달성률</span>
        <BigNumber value={rate ?? "-"} unit="%" />
      </div>
      <div className="my-week-bars">
        {WEEKDAYS.map((label, i) => {
          const day = days?.[i];
          const future = !day || day.date > todayKey;
          const percent = !future && day.expected > 0 ? Math.round((day.taken / day.expected) * 100) : null;
          let state = "future";
          if (!future) state = percent === null ? "none" : percent >= 100 ? "full" : percent > 0 ? "part" : "zero";
          const title = future
            ? `${label}요일 예정`
            : percent === null
              ? `${label}요일 먹을 영양제 없음`
              : `${label}요일 ${day.taken}/${day.expected} 복용`;
          return (
            <div key={label} className={`my-week-bar is-${state}${day?.date === todayKey ? " is-today" : ""}`}>
              <div className="my-week-track">
                <span
                  aria-label={title}
                  role="img"
                  style={state === "full" || state === "part" ? { height: `${Math.max(percent, 8)}%` } : undefined}
                />
              </div>
              <em>{label}</em>
            </div>
          );
        })}
      </div>
    </section>
  );
}

// 3. 이번 달 달성률 + 주차별 꺾은선 (진행 중인 주는 속이 빈 점)
const COL = 62; // 주차 1칸 너비 (viewBox 단위)
const TOP = 12;
const BOTTOM = 88;

function MonthCard({ rate, weeks, todayKey }) {
  const list = weeks ?? [];
  const points = list
    .map((w, i) => ({ ...w, i }))
    .filter((w) => w.rate !== null && w.rate !== undefined);
  // 값 차이가 잘 보이도록 최솟값 아래 10%p 부터 100까지를 세로 범위로 사용
  const low = points.length ? Math.max(0, Math.floor((Math.min(...points.map((p) => p.rate)) - 10) / 10) * 10) : 0;
  const y = (v) => BOTTOM - ((v - low) / (100 - low || 1)) * (BOTTOM - TOP);
  const x = (i) => COL / 2 + i * COL;
  const line = points.map((p, n) => `${n ? "L" : "M"}${x(p.i)},${y(p.rate).toFixed(1)}`).join(" ");
  const area = points.length > 1 ? `${line} L${x(points.at(-1).i)},96 L${x(points[0].i)},96 Z` : "";
  const isCurrent = (w) => w.start <= todayKey && todayKey <= w.end;
  const width = Math.max(list.length, 1) * COL;

  return (
    <section className="my-stat-card">
      <div className="my-stat-top">
        <span className="my-stat-label">이번 달 달성률</span>
        <BigNumber value={rate ?? "-"} unit="%" />
      </div>
      {points.length === 0 ? (
        <p className="my-month-empty">이번 달 기록이 쌓이면 주별 추이를 보여드릴게요</p>
      ) : (
        <>
          <svg
            className="my-month-chart"
            viewBox={`0 0 ${width} 100`}
            role="img"
            aria-label={points.map((p) => `${p.week}주 ${p.rate}%`).join(", ")}
          >
            <defs>
              <linearGradient id="my-month-fill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="var(--primary)" stopOpacity="0.18" />
                <stop offset="1" stopColor="var(--primary)" stopOpacity="0" />
              </linearGradient>
            </defs>
            {area && <path d={area} fill="url(#my-month-fill)" />}
            {points.length > 1 && <path d={line} className="my-month-line" />}
            {points.map((p) => (
              <circle
                key={p.week}
                cx={x(p.i)}
                cy={y(p.rate)}
                r={isCurrent(p) ? 5 : 3.5}
                className={isCurrent(p) ? "my-month-dot is-current" : "my-month-dot"}
              />
            ))}
          </svg>
          <div className="my-month-labels">
            {list.map((w) => (
              <span key={w.week} className={isCurrent(w) ? "is-current" : ""}>
                {w.week}주
              </span>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

export default function MyStats({ stats }) {
  const todayKey = kstTodayKey();
  return (
    <div className="my-stats">
      <StreakCard streak={stats?.streakDays ?? null} best={stats?.bestStreakDays ?? 0} />
      <WeekCard rate={stats?.weeklyRate} days={stats?.weekDays} todayKey={todayKey} />
      <MonthCard rate={stats?.monthlyRate} weeks={stats?.monthWeeks} todayKey={todayKey} />
    </div>
  );
}
