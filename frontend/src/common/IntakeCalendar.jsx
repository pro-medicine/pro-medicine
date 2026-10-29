import { useEffect, useRef, useState } from "react";
import { Icon } from "../shared/ui";
import { useModalFocus } from "./useModalFocus";
import "./calendar.css";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
const pad = (n) => String(n).padStart(2, "0");

const EVENT_TYPES = [
  { value: "INJECTION", label: "주사" },
  { value: "MEDICATION", label: "처방약" },
  { value: "ONE_TIME_INTAKE", label: "일회성 복용" },
  { value: "APPOINTMENT", label: "병원 방문" },
  { value: "OTHER", label: "기타" },
];
const typeLabel = (value) =>
  EVENT_TYPES.find((t) => t.value === value)?.label ?? "기타";

const EMPTY_FORM = {
  eventType: "MEDICATION",
  title: "",
  time: "09:00",
  notes: "",
};

// ───────── 날짜 모달 ─────────
function DayModal({
  dateKey,
  showIntake,
  dayItems,
  dayError,
  events,
  canEdit,
  openerRef,
  onClose,
  onSave,
  onDelete,
}) {
  const dialogRef = useModalFocus(onClose, openerRef);
  const [mode, setMode] = useState(null); // null | "new" | 이벤트 객체(수정)
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const openNew = () => {
    setForm(EMPTY_FORM);
    setError("");
    setMode("new");
  };

  const openEdit = (event) => {
    setForm({
      eventType: event.eventType,
      title: event.title,
      time: event.scheduledAt?.slice(11, 16) || "09:00",
      notes: event.notes ?? "",
    });
    setError("");
    setMode(event);
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setError("일정 이름을 입력해 주세요.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave(
        {
          eventType: form.eventType,
          title: form.title.trim(),
          scheduledAt: `${dateKey}T${form.time}:00`,
          notes: form.notes.trim() || null,
        },
        mode !== "new" ? mode.eventId : null,
      );
      setMode(null);
    } catch (err) {
      setError(err.message || "일정을 저장하지 못했어요.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (event) => {
    if (!window.confirm(`'${event.title}' 일정을 삭제할까요?`)) return;
    try {
      await onDelete(event.eventId);
    } catch (err) {
      setError(err.message || "일정을 삭제하지 못했어요.");
    }
  };

  return (
    <div className="modal-layer" role="presentation" onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className="modal-card calendar-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`${dateKey} 기록`}
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <span className="section-label">{dateKey.slice(0, 4)}</span>
            <h2>
              {Number(dateKey.slice(5, 7))}월 {Number(dateKey.slice(8))}일
            </h2>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </div>

        {showIntake && (
          <div className="calendar-modal-section">
            <h3>복용 기록</h3>
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

        {canEdit && (
          <div className="calendar-modal-section">
            <div className="calendar-modal-row">
              <h3>일정</h3>
              {!mode && (
                <button
                  type="button"
                  className="event-add-button"
                  onClick={openNew}
                >
                  <Icon name="plus" size={14} />
                  일정 추가
                </button>
              )}
            </div>

            {!mode && events.length === 0 && (
              <p className="calendar-summary">등록된 일정이 없어요.</p>
            )}

            {!mode && (
              <ul>
                {events.map((event) => (
                  <li key={event.eventId} className="event-item">
                    <span className="event-type">
                      {typeLabel(event.eventType)}
                    </span>
                    <span className="day-copy">
                      <strong>{event.title}</strong>
                      <small>
                        {event.scheduledAt?.slice(11, 16)}
                        {event.notes ? ` · ${event.notes}` : ""}
                      </small>
                    </span>
                    <span className="event-actions">
                      <button type="button" onClick={() => openEdit(event)}>
                        수정
                      </button>
                      <button type="button" onClick={() => remove(event)}>
                        삭제
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            )}

            {mode && (
              <form className="event-form" onSubmit={submit}>
                <label>
                  종류
                  <select
                    value={form.eventType}
                    onChange={(e) =>
                      setForm({ ...form, eventType: e.target.value })
                    }
                  >
                    {EVENT_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  이름
                  <input
                    autoFocus
                    maxLength={150}
                    placeholder="예: 독감 예방접종"
                    value={form.title}
                    onChange={(e) =>
                      setForm({ ...form, title: e.target.value })
                    }
                  />
                </label>
                <label>
                  시각
                  <input
                    type="time"
                    value={form.time}
                    onChange={(e) => setForm({ ...form, time: e.target.value })}
                  />
                </label>
                <label>
                  메모
                  <textarea
                    maxLength={1000}
                    rows={2}
                    value={form.notes}
                    onChange={(e) =>
                      setForm({ ...form, notes: e.target.value })
                    }
                  />
                </label>
                <div className="event-form-actions">
                  <button
                    type="button"
                    onClick={() => setMode(null)}
                    disabled={saving}
                  >
                    취소
                  </button>
                  <button type="submit" className="primary" disabled={saving}>
                    {saving ? "저장 중..." : "저장"}
                  </button>
                </div>
              </form>
            )}

            {error && <p className="calendar-error">{error}</p>}
          </div>
        )}
      </div>
    </div>
  );
}

// ───────── 달력 ─────────
export default function IntakeCalendar({
  fetchDates,
  fetchDay,
  fetchEvents,
  createEvent,
  updateEvent,
  deleteEvent,
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
  const [events, setEvents] = useState([]);
  const openerRef = useRef(null);

  const monthKey = `${ym.year}-${pad(ym.month)}`;
  const todayKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const isCurrentMonth =
    ym.year === now.getFullYear() && ym.month === now.getMonth() + 1;
  const daysInMonth = new Date(ym.year, ym.month, 0).getDate();
  const canEdit = Boolean(createEvent);

  const reloadEvents = async () => {
    const list = await fetchEvents(
      `${monthKey}-01`,
      `${monthKey}-${pad(daysInMonth)}`,
    );
    setEvents(list ?? []);
  };

  // 완료한 날
  useEffect(() => {
    let ignore = false;
    setError("");
    setDates([]);
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

  // 일정 (점 표시용, 내 달력만)
  useEffect(() => {
    if (!fetchEvents) return;
    let ignore = false;
    fetchEvents(`${monthKey}-01`, `${monthKey}-${pad(daysInMonth)}`)
      .then((list) => {
        if (!ignore) setEvents(list ?? []);
      })
      .catch(() => {
        if (!ignore) setEvents([]); // 점이 안 보일 뿐 달력은 유지
      });
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [monthKey]);

  // 선택한 날의 복용 기록 (오늘까지만: 미래 날짜는 기록이 아니라 계획이라 조회 안 함)
  useEffect(() => {
    if (!selected || !fetchDay || selected > todayKey) return;
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected]);

  const eventsForDay = (key) =>
    events.filter((event) => event.scheduledAt?.slice(0, 10) === key);

  const move = (delta) => {
    setSelected(null);
    setYm(({ year, month }) => {
      const d = new Date(year, month - 1 + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() + 1 };
    });
  };

  const handleSave = async (payload, eventId) => {
    if (eventId) await updateEvent(eventId, payload);
    else await createEvent(payload);
    await reloadEvents();
  };

  const handleDelete = async (eventId) => {
    await deleteEvent(eventId);
    await reloadEvents();
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
          <button type="button" aria-label="다음 달" onClick={() => move(1)}>
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
          const dayEvents = eventsForDay(key);
          const isFuture = key > todayKey;
          // 일정 기능이 있으면 미래도 열고, 없으면(친구 달력) 과거 기록만 연다
          const disabled = canEdit ? false : !fetchDay || isFuture;
          const cls = [
            "calendar-day",
            completed.has(key) ? "done" : "",
            key === todayKey ? "today" : "",
            isFuture ? "future" : "",
            selected === key ? "selected" : "",
          ].join(" ");
          return (
            <button
              key={key}
              type="button"
              disabled={disabled}
              className={cls}
              aria-label={
                `${day}일` +
                (completed.has(key) ? " 복용 완료" : "") +
                (dayEvents.length ? ` 일정 ${dayEvents.length}개` : "")
              }
              onClick={(e) => {
                openerRef.current = e.currentTarget;
                setSelected(key);
              }}
            >
              {day}
              {dayEvents.length > 0 && <span className="calendar-event-dot" />}
            </button>
          );
        })}
      </div>

      {error && <p className="calendar-error">{error}</p>}
      {!error && (
        <p className="calendar-summary">
          {elapsed}일 중 <b>{completed.size}일</b> 완료
        </p>
      )}

      {selected && (
        <DayModal
          dateKey={selected}
          showIntake={Boolean(fetchDay) && selected <= todayKey}
          dayItems={dayItems}
          dayError={dayError}
          events={eventsForDay(selected)}
          canEdit={canEdit}
          openerRef={openerRef}
          onClose={() => setSelected(null)}
          onSave={handleSave}
          onDelete={handleDelete}
        />
      )}
    </section>
  );
}
