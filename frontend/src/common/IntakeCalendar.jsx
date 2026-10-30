import { useEffect, useState } from "react";
import { Icon } from "../shared/ui";
import "./calendar.css";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
const pad = (n) => String(n).padStart(2, "0");

// 날짜 칸 진행 링 (#67 마이페이지 시안 ③: 그날 챙긴 비율만큼 초록 링이 채워짐)
const RING = 34;
const RING_R = 15;
const RING_C = 2 * Math.PI * RING_R;

function DayRing({ ratio }) {
  return (
    <svg className="calendar-ring" width={RING} height={RING} viewBox={`0 0 ${RING} ${RING}`} aria-hidden="true">
      <circle cx={RING / 2} cy={RING / 2} r={RING_R} className="calendar-ring-track" />
      {ratio > 0 && (
        <circle
          cx={RING / 2}
          cy={RING / 2}
          r={RING_R}
          className="calendar-ring-fill"
          strokeDasharray={RING_C}
          strokeDashoffset={RING_C * (1 - Math.min(ratio, 1))}
          transform={`rotate(-90 ${RING / 2} ${RING / 2})`}
        />
      )}
    </svg>
  );
}

// fetchProgress가 있으면 날짜별 { date, expected, taken } 으로 일부 복용까지 링으로 보여주고,
// 없으면 fetchDates(모두 복용한 날짜 목록)로 완료한 날만 꽉 찬 링으로 보여준다 (친구 달력)
export default function IntakeCalendar({
  fetchDates,
  fetchProgress,
  fetchDay,
  title = "복용 달력",
}) {
  const now = new Date();
  const [ym, setYm] = useState({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
  });
  const [dates, setDates] = useState([]);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [dayItems, setDayItems] = useState(null);
  const [dayError, setDayError] = useState("");

  const monthKey = `${ym.year}-${pad(ym.month)}`;
  const todayKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const isCurrentMonth =
    ym.year === now.getFullYear() && ym.month === now.getMonth() + 1;
  const daysInMonth = new Date(ym.year, ym.month, 0).getDate();

  useEffect(() => {
    let ignore = false;
    setError("");
    setDates([]);
    (fetchProgress ?? fetchDates)(`${monthKey}-01`, `${monthKey}-${pad(daysInMonth)}`)
      .then((res) => {
        if (!ignore) setDates(res ?? []);
      })
      .catch((err) => {
        if (!ignore) setError(err.message || "달력을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
    // fetchDates·fetchProgress는 매 렌더마다 새 함수라 의존성에서 제외 (친구가 바뀌면 부모가 key로 새로 마운트)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [monthKey]);

  useEffect(() => {
    if (!selected || !fetchDay) return;
    let ignore = false;
    setDayItems(null);
    setDayError("");
    fetchDay(selected)
      .then((list) => {
        if (!ignore) setDayItems(list ?? []);
      })
      .catch((err) => {
        if (!ignore) setDayError(err.message || "기록을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [selected]);

  const move = (delta) => {
    setSelected(null);
    setYm(({ year, month }) => {
      const d = new Date(year, month - 1 + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() + 1 };
    });
  };

  // { "YYYY-MM-DD" → { expected, taken } } (날짜 문자열만 오면 모두 복용한 날)
  const progress = new Map(
    dates.map((item) =>
      typeof item === "string"
        ? [item, { expected: 1, taken: 1 }]
        : [item.date, { expected: item.expected, taken: item.taken }],
    ),
  );
  const completed = new Set(
    [...progress].filter(([, p]) => p.expected > 0 && p.taken >= p.expected).map(([date]) => date),
  );
  const offset = (new Date(ym.year, ym.month - 1, 1).getDay() + 6) % 7; // 월요일 시작
  const cells = [
    ...Array(offset).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const elapsed = isCurrentMonth
    ? now.getDate()
    : new Date(ym.year, ym.month - 1, 1) > now
      ? 0
      : daysInMonth;

  return (
    <section className="section-block intake-calendar">
      <div className="calendar-head">
        <h2>{title}</h2>
        <div className="calendar-nav">
          <button type="button" aria-label="이전 달" onClick={() => move(-1)}>
            <Icon name="arrow" size={18} />
          </button>
          <span>
            {ym.year}년 {ym.month}월
          </span>
          <button
            type="button"
            aria-label="다음 달"
            disabled={isCurrentMonth}
            onClick={() => move(1)}
          >
            <Icon name="chevron" size={18} />
          </button>
        </div>
      </div>

      <div className="calendar-grid calendar-weekdays">
        {WEEKDAYS.map((w) => (
          <span key={w}>{w}</span>
        ))}
      </div>

      <div className="calendar-grid">
        {cells.map((day, i) => {
          if (day === null) return <span key={`e${i}`} />;
          const key = `${monthKey}-${pad(day)}`;
          const future = key > todayKey;
          const p = progress.get(key);
          const ratio = p && p.expected > 0 ? p.taken / p.expected : 0;
          // 링: 진행도 모드면 먹을 일정이 있던 날만, 완료 날짜 모드(친구)면 지난 날 모두
          const showRing = !future && (fetchProgress ? p?.expected > 0 : true);
          const cls = [
            "calendar-day",
            completed.has(key) ? "done" : ratio > 0 ? "partial" : "",
            key === todayKey ? "today" : "",
            future ? "future" : "",
            selected === key ? "selected" : "",
          ]
            .filter(Boolean)
            .join(" ");
          let label = `${day}일`;
          if (completed.has(key)) label = `${day}일 모두 복용`;
          else if (p && p.expected > 0) label = `${day}일 ${p.taken}/${p.expected} 복용`;
          return (
            <button
              key={key}
              type="button"
              disabled={!fetchDay || future}
              className={cls}
              aria-pressed={selected === key}
              aria-label={label}
              onClick={() => setSelected(selected === key ? null : key)}
            >
              {showRing && <DayRing ratio={ratio} />}
              <span className="calendar-day-num">{day}</span>
            </button>
          );
        })}
      </div>

      {error && <p className="calendar-error">{error}</p>}
      {selected && (
        <div className="calendar-day-detail">
          <h3>
            {Number(selected.slice(5, 7))}월 {Number(selected.slice(8))}일 복용
            기록
          </h3>
          {dayError && <p className="calendar-error">{dayError}</p>}
          {!dayError && dayItems === null && (
            <p className="calendar-summary">불러오는 중...</p>
          )}
          {dayItems?.length === 0 && (
            <p className="calendar-summary">이 날 먹을 영양제가 없었어요.</p>
          )}
          <ul>
            {(dayItems ?? []).map((item) => (
              <li key={item.id} className={item.taken ? "taken" : "missed"}>
                <span className="day-mark">{item.taken ? "✓" : "–"}</span>
                <span className="day-copy">
                  <strong>{item.name}</strong>
                  <small>{item.timing}</small>
                </span>
                <span className="day-status">
                  {item.taken ? "복용" : "미복용"}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {!error && (
        <div className="calendar-foot">
          <div className="calendar-legend">
            <span>
              <i className="calendar-legend-full" aria-hidden="true" />
              모두 복용
            </span>
            {fetchProgress && (
              <span>
                <i className="calendar-legend-part" aria-hidden="true" />
                일부 복용
              </span>
            )}
          </div>
          <p className="calendar-summary">
            {elapsed}일 중 <b>{completed.size}일</b> 완료
          </p>
        </div>
      )}
    </section>
  );
}
