import { useEffect, useRef, useState } from "react";
import { Icon } from "../shared/ui";
import { useModalFocus } from "../common/useModalFocus";
import "./calendar.css";
import PrescriptionFields, {
  makeEmptyPrescription,
  validatePrescription,
  toPrescriptionPayload,
} from "./PrescriptionFields";
import InteractionWarningList from "./InteractionWarningList";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
const pad = (n) => String(n).padStart(2, "0");

const RING = 34;
const RING_R = 15;
const RING_C = 2 * Math.PI * RING_R;

function DayRing({ ratio }) {
  return (
    <svg
      className="calendar-ring"
      width={RING}
      height={RING}
      viewBox={`0 0 ${RING} ${RING}`}
      aria-hidden="true"
    >
      <circle
        cx={RING / 2}
        cy={RING / 2}
        r={RING_R}
        className="calendar-ring-track"
      />
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

const EVENT_TYPES = [
  { value: "INJECTION", label: "주사" },
  { value: "MEDICATION", label: "상비약" },
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

// 상충 단계 (InteractionWarningList와 같은 이름·순서)
const SEVERITY_ORDER = ["AVOID", "CAUTION", "TIMING", "INFO"];
const SEVERITY_LABEL = {
  AVOID: "피하세요",
  CAUTION: "주의",
  TIMING: "간격 두기",
  INFO: "참고",
};

// 처방 하나의 경고 중 가장 심각한 단계. 경고가 없으면 null (배지를 안 띄움)
const topSeverity = (data) => {
  const found = new Set((data?.warnings ?? []).map((w) => w.severity));
  return SEVERITY_ORDER.find((s) => found.has(s)) ?? null;
};

// ───────── 일정 상세 (읽기 전용) ─────────
// interaction: null(상충 기능 없음) | { status: "loading" | "done" | "error", data }
function EventDetail({ event, interaction, onBack, onEdit }) {
  const date = event.scheduledAt?.slice(0, 10);
  const time = event.scheduledAt?.slice(11, 16);

  return (
    <div className="event-detail">
      <button type="button" className="event-detail-back" onClick={onBack}>
        ← 목록으로
      </button>

      <div className="event-detail-head">
        <span className="event-type">{typeLabel(event.eventType)}</span>
        <strong>{event.title}</strong>
      </div>

      <dl className="event-detail-meta">
        {date && (
          <div>
            <dt>날짜</dt>
            <dd>
              {Number(date.slice(5, 7))}월 {Number(date.slice(8))}일
            </dd>
          </div>
        )}
        {time && (
          <div>
            <dt>시각</dt>
            <dd>{time}</dd>
          </div>
        )}
        {event.notes && (
          <div>
            <dt>메모</dt>
            <dd>{event.notes}</dd>
          </div>
        )}
      </dl>

      {event.prescriptionId && interaction && (
        <div className="event-detail-section">
          {interaction.status === "error" ? (
            <p className="interaction-empty">상충 정보를 불러오지 못했어요.</p>
          ) : interaction.status === "done" ? (
            <InteractionWarningList data={interaction.data} />
          ) : (
            <p className="interaction-empty">상충 정보를 확인하고 있어요...</p>
          )}
        </div>
      )}

      <div className="event-form-actions">
        <button type="button" onClick={onBack}>
          닫기
        </button>
        <button type="button" className="primary" onClick={onEdit}>
          수정
        </button>
      </div>
    </div>
  );
}

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
  onSavePrescription,
  updatePrescription,
  onDelete,
  onToggle,
  fetchInteractions,
}) {
  const dialogRef = useModalFocus(onClose, openerRef);
  const [mode, setMode] = useState(null); // null | "new" | event 객체(수정)
  const [viewEvent, setViewEvent] = useState(null); // 상세로 보고 있는 일정
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [rx, setRx] = useState(() => makeEmptyPrescription(dateKey));

  // prescriptionId → { status: "loading" | "done" | "error", data }
  const [interactionMap, setInteractionMap] = useState({});

  // 이 날 일정에 걸린 처방들의 상충 정보를 한 번씩만 조회 (목록 배지 + 상세에서 같이 사용)
  const prescriptionIds = [
    ...new Set(events.map((e) => e.prescriptionId).filter(Boolean)),
  ];
  const idsKey = prescriptionIds.join(",");

  useEffect(() => {
    if (!fetchInteractions || prescriptionIds.length === 0) return;
    let ignore = false;
    prescriptionIds.forEach((id) => {
      setInteractionMap((prev) => ({ ...prev, [id]: { status: "loading" } }));
      fetchInteractions(id)
        .then((data) => {
          if (!ignore) {
            setInteractionMap((prev) => ({
              ...prev,
              [id]: { status: "done", data },
            }));
          }
        })
        .catch(() => {
          if (!ignore) {
            setInteractionMap((prev) => ({
              ...prev,
              [id]: { status: "error" },
            }));
          }
        });
    });
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idsKey]);

  const interactionOf = (event) =>
    fetchInteractions && event.prescriptionId
      ? (interactionMap[event.prescriptionId] ?? { status: "loading" })
      : null;

  const openNew = () => {
    setForm(EMPTY_FORM);
    setError("");
    setViewEvent(null);
    setMode("new");
    setRx(makeEmptyPrescription(dateKey));
  };

  const openEdit = (event) => {
    const scheduledTime = event.scheduledAt?.slice(11, 16) || "09:00";
    setForm({
      eventType: event.eventType,
      title: event.title,
      time: scheduledTime,
      notes: event.notes ?? "",
    });

    if (event.prescriptionId) {
      const interactionData = interactionMap[event.prescriptionId]?.data;
      setRx({
        name: event.title,
        medicineId: event.medicineId ?? null,
        prescriptionId: event.prescriptionId,
        startAt: interactionData?.startAt || dateKey,
        endAt: interactionData?.endAt || dateKey,
        scheduledTime: scheduledTime,
        oneDay:
          interactionData?.startAt &&
          interactionData?.endAt &&
          interactionData.startAt === interactionData.endAt,
        openEnded: interactionData?.startAt && !interactionData?.endAt,
        notes: event.notes ?? "",
      });
    }
    setError("");
    setViewEvent(null);
    setMode(event);
  };

  const isRx =
    (mode === "new" &&
      form.eventType === "MEDICATION" &&
      Boolean(onSavePrescription)) ||
    (mode !== null && mode !== "new" && Boolean(mode.prescriptionId));

  const submit = async (e) => {
    e.preventDefault();
    if (isRx) {
      const message = validatePrescription(rx);
      if (message) {
        setError(message);
        return;
      }
      setSaving(true);
      setError("");
      try {
        if (mode === "new") {
          await onSavePrescription(toPrescriptionPayload(rx));
        } else {
          if (updatePrescription) {
            await onSave(
              {
                eventType: "MEDICATION",
                title: rx.name.trim(),
                scheduledAt: `${dateKey}T${rx.scheduledTime || "09:00"}:00`,
                notes: rx.notes.trim() || null,
              },
              mode.eventId,
            );
          }
        }
        setMode(null);
      } catch (err) {
        setError(err.message || "약을 저장하지 못했어요.");
      } finally {
        setSaving(false);
      }
      return;
    }

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

  const remove = async (event, scope = "one") => {
    const message =
      scope === "all"
        ? `'${event.title}'의 모든 날짜 일정을 삭제할까요?`
        : event.prescriptionId
          ? `${Number(dateKey.slice(5, 7))}월 ${Number(dateKey.slice(8))}일의 '${event.title}' 일정만 삭제할까요?`
          : `'${event.title}' 일정을 삭제할까요?`;
    if (!window.confirm(message)) return;
    try {
      await onDelete(event, scope);
    } catch (err) {
      setError(err.message || "일정을 삭제하지 못했어요.");
    }
  };

  const toggle = async (event) => {
    setError("");
    try {
      await onToggle(event);
    } catch (err) {
      setError(err.message || "상태를 바꾸지 못했어요.");
    }
  };

  const showList = !mode && !viewEvent;

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
              {showList && (
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

            {showList && events.length === 0 && (
              <p className="calendar-summary">등록된 일정이 없어요.</p>
            )}

            {showList && (
              <ul>
                {events.map((event) => {
                  const severity = event.prescriptionId
                    ? topSeverity(interactionMap[event.prescriptionId]?.data)
                    : null;
                  return (
                    <li
                      key={event.eventId}
                      className={`event-item ${event.status === "COMPLETED" ? "completed" : ""}`}
                    >
                      {onToggle && (
                        <button
                          type="button"
                          className="event-check"
                          role="checkbox"
                          aria-checked={event.status === "COMPLETED"}
                          aria-label={`${event.title} 완료`}
                          onClick={() => toggle(event)}
                        >
                          {event.status === "COMPLETED" ? "✓" : ""}
                        </button>
                      )}
                      <span className="event-type">
                        {typeLabel(event.eventType)}
                      </span>
                      {/* 제목·시각을 누르면 읽기 전용 상세로 */}
                      <button
                        type="button"
                        className="day-copy event-open"
                        aria-label={`${event.title} 상세 보기`}
                        onClick={() => setViewEvent(event)}
                      >
                        <strong>{event.title}</strong>
                        {event.scheduledAt && (
                          <small className="event-time-badge">
                            {event.scheduledAt.slice(11, 16)}
                            {severity && (
                              <span
                                className={`interaction-badge ${severity.toLowerCase()}`}
                              >
                                {SEVERITY_LABEL[severity]}
                              </span>
                            )}
                          </small>
                        )}
                      </button>
                      <span className="event-actions">
                        <button type="button" onClick={() => openEdit(event)}>
                          수정
                        </button>
                        <button
                          type="button"
                          onClick={() => remove(event, "one")}
                        >
                          {event.prescriptionId ? "삭제" : "전체 삭제"}
                        </button>
                        {event.prescriptionId && (
                          <button
                            type="button"
                            className="danger"
                            onClick={() => remove(event, "all")}
                          >
                            전체 삭제
                          </button>
                        )}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}

            {viewEvent && !mode && (
              <EventDetail
                event={viewEvent}
                interaction={interactionOf(viewEvent)}
                onBack={() => setViewEvent(null)}
                onEdit={() => openEdit(viewEvent)}
              />
            )}

            {mode && (
              <form className="event-form" onSubmit={submit}>
                <label>
                  종류
                  <select
                    value={form.eventType}
                    disabled={mode !== "new" && Boolean(mode.prescriptionId)}
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
                {isRx ? (
                  <PrescriptionFields value={rx} onChange={setRx} />
                ) : (
                  <>
                    <label>
                      이름
                      <input
                        autoFocus
                        maxLength={30}
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
                        onChange={(e) =>
                          setForm({ ...form, time: e.target.value })
                        }
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
                  </>
                )}
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

export default function IntakeCalendar({
  fetchDates,
  fetchProgress,
  fetchDay,
  fetchEvents,
  createEvent,
  updateEvent,
  updateEventStatus,
  deleteEvent,
  createPrescription,
  updatePrescription,
  deletePrescription,
  fetchInteractions,
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
    try {
      const list = await fetchEvents(
        `${monthKey}-01`,
        `${monthKey}-${pad(daysInMonth)}`,
      );
      setEvents(list ?? []);
    } catch (err) {
      setError(err.message || "일정을 불러오지 못했어요.");
    }
  };

  useEffect(() => {
    let ignore = false;
    setError("");
    setDates([]);
    (fetchProgress ?? fetchDates)(
      `${monthKey}-01`,
      `${monthKey}-${pad(daysInMonth)}`,
    )
      .then((res) => {
        if (!ignore) setDates(res ?? []);
      })
      .catch((err) => {
        if (!ignore) setError(err.message || "달력을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [monthKey]);

  useEffect(() => {
    if (!fetchEvents) return;
    let ignore = false;
    fetchEvents(`${monthKey}-01`, `${monthKey}-${pad(daysInMonth)}`)
      .then((list) => {
        if (!ignore) setEvents(list ?? []);
      })
      .catch(() => {
        if (!ignore) setEvents([]);
      });
    return () => {
      ignore = true;
    };
  }, [monthKey]);

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

  const handleSavePrescription = async (payload) => {
    await createPrescription(payload);
    await reloadEvents();
  };

  const handleDelete = async (event, scope = "one") => {
    if (scope === "all" && event.prescriptionId && deletePrescription) {
      await deletePrescription(event.prescriptionId);
    } else {
      await deleteEvent(event.eventId);
    }
    await reloadEvents();
  };

  const handleToggle = async (event) => {
    const next = event.status === "COMPLETED" ? "PLANNED" : "COMPLETED";
    await updateEventStatus(event.eventId, next);
    await reloadEvents();
  };

  const progress = new Map(
    dates.map((item) =>
      typeof item === "string"
        ? [item, { expected: 1, taken: 1 }]
        : [item.date, { expected: item.expected, taken: item.taken }],
    ),
  );
  const completed = new Set(
    [...progress]
      .filter(([, p]) => p.expected > 0 && p.taken >= p.expected)
      .map(([date]) => date),
  );
  const offset = (new Date(ym.year, ym.month - 1, 1).getDay() + 6) % 7;
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
          const disabled = canEdit ? false : !fetchDay || isFuture;
          const p = progress.get(key);
          const ratio = p && p.expected > 0 ? p.taken / p.expected : 0;
          const showRing =
            !isFuture && (fetchProgress ? p?.expected > 0 : true);
          const cls = [
            "calendar-day",
            completed.has(key) ? "done" : ratio > 0 ? "partial" : "",
            key === todayKey ? "today" : "",
            isFuture ? "future" : "",
            selected === key ? "selected" : "",
          ]
            .filter(Boolean)
            .join(" ");
          return (
            <button
              key={key}
              type="button"
              disabled={disabled}
              className={cls}
              aria-label={
                `${day}일` +
                (completed.has(key)
                  ? " 모두 복용"
                  : p && p.expected > 0
                    ? ` ${p.taken}/${p.expected} 복용`
                    : "") +
                (dayEvents.length ? ` 일정 ${dayEvents.length}개` : "")
              }
              onClick={(e) => {
                openerRef.current = e.currentTarget;
                setSelected(key);
              }}
            >
              {showRing && <DayRing ratio={ratio} />}
              <span className="calendar-day-num">{day}</span>
              {dayEvents.length > 0 && (
                <span
                  className={`calendar-event-dot ${
                    dayEvents.every((e) => e.status === "COMPLETED")
                      ? "all-done"
                      : ""
                  }`}
                />
              )}
            </button>
          );
        })}
      </div>

      {error && <p className="calendar-error">{error}</p>}
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
            {canEdit && (
              <span>
                <i className="calendar-legend-event" aria-hidden="true" />
                일정
              </span>
            )}
          </div>
          <p className="calendar-summary">
            {elapsed}일 중 <b>{completed.size}일</b> 완료
          </p>
        </div>
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
          onSavePrescription={
            createPrescription ? handleSavePrescription : null
          }
          updatePrescription={async (rxId, payload) => {
            await updatePrescription(rxId, payload);
            await reloadEvents();
          }}
          onDelete={handleDelete}
          onToggle={updateEventStatus ? handleToggle : null}
          fetchInteractions={fetchInteractions}
        />
      )}
    </section>
  );
}
