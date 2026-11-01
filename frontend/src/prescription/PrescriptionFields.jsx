// 약 등록용 입력 필드 (IntakeCalendar의 DayModal 폼 안에서 종류가 "약"일 때 사용)
// 복용 시간은 받지 않고 복용 기간만 받는다.

export const makeEmptyPrescription = (startDate) => ({
  name: "",
  startAt: startDate,
  endAt: startDate,
  oneDay: false,
  openEnded: false,
  notes: "",
});

export function validatePrescription(rx) {
  if (!rx.name.trim()) return "약 이름을 입력해 주세요.";
  if (!rx.startAt) return "시작일을 선택해 주세요.";
  if (!rx.oneDay && !rx.openEnded) {
    if (!rx.endAt) return "종료일을 선택해 주세요.";
    if (rx.endAt < rx.startAt) return "종료일은 시작일 이후여야 해요.";
    const days = (new Date(rx.endAt) - new Date(rx.startAt)) / 86400000 + 1;
    if (days > 62) return "복용 기간은 최대 62일까지 등록할 수 있어요.";
  }
  return "";
}

export const toPrescriptionPayload = (rx) => ({
  name: rx.name.trim(),
  startAt: rx.startAt,
  endAt: rx.oneDay ? rx.startAt : rx.openEnded ? null : rx.endAt,
  notes: rx.notes.trim() || null,
});

export default function PrescriptionFields({ value, onChange }) {
  const set = (patch) => onChange({ ...value, ...patch });
  const endLocked = value.oneDay || value.openEnded;

  return (
    <>
      <label>
        약 이름
        <input
          autoFocus
          maxLength={30}
          placeholder="예: 타이레놀, 아목시실린"
          value={value.name}
          onChange={(e) => set({ name: e.target.value })}
        />
      </label>

      <div className="rx-period">
        <label>
          시작일
          <input
            type="date"
            value={value.startAt}
            onChange={(e) => set({ startAt: e.target.value })}
          />
        </label>
        <label>
          종료일
          <input
            type="date"
            value={value.oneDay ? value.startAt : value.endAt}
            min={value.startAt}
            disabled={endLocked}
            onChange={(e) => set({ endAt: e.target.value })}
          />
        </label>
      </div>

      <label className="rx-check">
        <input
          type="checkbox"
          checked={value.oneDay}
          onChange={(e) => set({ oneDay: e.target.checked, openEnded: false })}
        />
        하루만 먹어요
      </label>
      <label className="rx-check">
        <input
          type="checkbox"
          checked={value.openEnded}
          disabled={value.oneDay}
          onChange={(e) => set({ openEnded: e.target.checked })}
        />
        종료일 미정 (우선 3일치만 등록돼요)
      </label>

      <label>
        메모
        <textarea
          maxLength={1000}
          rows={2}
          value={value.notes}
          onChange={(e) => set({ notes: e.target.value })}
        />
      </label>
    </>
  );
}
