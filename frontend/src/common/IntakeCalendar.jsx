import { useEffect, useState } from "react";
import { Icon } from "../shared/ui";
import "./calendar.css";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
const pad = (n) => String(n).padStart(2, "0");

export default function IntakeCalendar({
  fetchDates,
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
    fetchDates(`${monthKey}-01`, `${monthKey}-${pad(daysInMonth)}`)
      .then((res) => {
        if (!ignore) setDates(res ?? []);
      })
      .catch((err) => {
        if (!ignore) setError(err.message || "달력을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
    // fetchDates는 매 렌더마다 새 함수라 의존성에서 제외 (친구가 바뀌면 부모가 key로 새로 마운트)
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

  const completed = new Set(dates);
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
          const cls = [
            "calendar-day",
            completed.has(key) ? "done" : "",
            key === todayKey ? "today" : "",
            key > todayKey ? "future" : "",
          ].join(" ");
          return (
            <button
              key={key}
              type="button"
              disabled={!fetchDay || key > todayKey}
              className={`${cls} ${selected === key ? "selected" : ""}`}
              aria-pressed={selected === key}
              aria-label={
                completed.has(key) ? `${day}일 복용 완료` : `${day}일`
              }
              onClick={() => setSelected(selected === key ? null : key)}
            >
              {day}
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
        <p className="calendar-summary">
          {elapsed}일 중 <b>{completed.size}일</b> 완료
        </p>
      )}
    </section>
  );
}
