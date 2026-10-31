// 오늘의 복용 타임라인 (#67 홈 시안 A, 색은 초록 3 시안) — 아침 → 점심 → 저녁을 시간 순서대로 보여주고 복용 체크
// 각 줄(카드)을 누르면 복용 체크 / 해제. "지금" 선은 홈이 오늘 날짜를 보여줄 때만 표시한다.
import { useEffect, useState } from "react";
import { kstToday } from "./homeApi";

const SLOT_LABEL = { BREAKFAST: "아침", LUNCH: "점심", DINNER: "저녁" };

// 지금 시각(KST) "HH:mm"
function nowHourMinute(now) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(now);
}

function toMinutes(hm) {
  const [h, m] = hm.split(":").map(Number);
  return h * 60 + m;
}

// 알림까지 남은 시간 / 지난 시간 문구
function relativeText(remindAt, nowHm) {
  const diff = toMinutes(remindAt) - toMinutes(nowHm);
  const abs = Math.abs(diff);
  const text = abs < 60 ? `${abs}분` : `${Math.floor(abs / 60)}시간${abs % 60 ? ` ${abs % 60}분` : ""}`;
  if (diff > 0) return `${text} 뒤`;
  if (diff === 0) return "지금";
  return `${text} 지남`;
}

export default function TodaySupplements({ supplements, done, total, pendingIds = [], onToggle, today }) {
  const [now, setNow] = useState(() => new Date()); // "지금" 선과 남은 시간을 1분마다 갱신

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const isToday = today === kstToday();
  const nowHm = nowHourMinute(now);
  const nextId = supplements.find((s) => !s.taken)?.id; // 다음에 챙길 영양제
  // "지금" 선 위치: 알림 시각이 지금보다 늦은 첫 줄 앞 (없으면 맨 끝)
  let nowIndex = supplements.findIndex((s) => s.remindAt && s.remindAt > nowHm);
  if (nowIndex === -1) nowIndex = supplements.length;

  const nowLine = (
    <li className="home-tl-now" aria-hidden="true" key="now">
      <span>지금 {nowHm}</span>
    </li>
  );

  return (
    <section className="home-today">
      <div className="home-card-head">
        <h3 className="home-card-title">오늘의 복용 타임라인</h3>
        {total > 0 && <span className="home-tl-chip">{done}/{total} 완료</span>}
      </div>

      {total === 0 ? (
        <p className="home-empty">
          오늘 복용할 영양제가 없어요. 마이페이지에서 영양제를 등록해보세요.
        </p>
      ) : (
        <ol className="home-tl">
          {supplements.map((s, index) => {
            const slot = SLOT_LABEL[s.mealSlot] ?? s.timing?.split(" ")[0] ?? "";
            const how = s.timing?.startsWith(slot) ? s.timing.slice(slot.length).trim() : s.timing;
            const status = s.taken ? "done" : s.id === nextId ? "next" : "later";
            const isLast = index === supplements.length - 1;
            const row = (
              <li key={s.id} className={`home-tl-row is-${status}${isLast ? " is-last" : ""}`}>
                <span className="home-tl-dot" aria-hidden="true">
                  {s.taken && (
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                      <path d="m5 12 4 4L19 6" />
                    </svg>
                  )}
                </span>
                <span className="home-tl-time">
                  <strong>{s.remindAt ?? ""}</strong>
                  <small>{slot}</small>
                </span>
                <button
                  type="button"
                  className="home-tl-box"
                  onClick={() => onToggle(s.id)}
                  aria-pressed={s.taken}
                  aria-label={`${s.name} ${s.taken ? "복용 체크 해제" : "복용 체크"}`}
                  disabled={pendingIds.includes(s.id)}
                >
                  <span className="home-tl-info">
                    <span className="home-tl-name">
                      <strong>{s.name}</strong>
                      {status === "next" && isToday && s.remindAt && (
                        <em>{relativeText(s.remindAt, nowHm)}</em>
                      )}
                    </span>
                    <small>{[how, s.desc].filter(Boolean).join(" · ")}</small>
                  </span>
                  {status === "done" && (
                    <span className="home-tl-note">
                      {s.completedAt ? `${s.completedAt}에 체크했어요` : "체크했어요"}
                    </span>
                  )}
                  {status === "next" && <span className="home-tl-action">먹었어요</span>}
                </button>
              </li>
            );
            return isToday && index === nowIndex ? [nowLine, row] : row;
          })}
          {isToday && nowIndex === supplements.length && nowLine}
        </ol>
      )}
    </section>
  );
}
