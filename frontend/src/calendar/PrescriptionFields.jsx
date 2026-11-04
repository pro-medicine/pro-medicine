import { useEffect, useRef, useState } from "react";
import { searchMedicines, previewInteractions } from "../api/calendarEventApi";
import InteractionWarningList from "./InteractionWarningList";

export const makeEmptyPrescription = (startDate) => ({
  name: "",
  medicineId: null,
  startAt: startDate,
  endAt: startDate,
  scheduledTime: "09:00",
  oneDay: false,
  openEnded: false,
  notes: "",
  prescriptionId: null,
});

// 유효성 검사
export function validatePrescription(rx) {
  if (!rx.name.trim()) return "약 이름을 입력해 주세요.";
  if (!rx.startAt) return "시작일을 선택해 주세요.";

  if (!rx.oneDay && !rx.openEnded) {
    if (!rx.endAt) return "종료일을 선택해 주세요.";

    if (rx.endAt < rx.startAt) {
      return "종료일은 시작일 이후여야 해요.";
    }

    const days = (new Date(rx.endAt) - new Date(rx.startAt)) / 86400000 + 1;

    if (days > 62) {
      return "복용 기간은 최대 62일까지 등록할 수 있어요.";
    }
  }

  return "";
}

// 서버 전송 데이터
export const toPrescriptionPayload = (rx) => ({
  name: rx.name.trim(),
  medicineId: rx.medicineId ?? null,
  startAt: rx.startAt,
  endAt: rx.oneDay ? rx.startAt : rx.openEnded ? null : rx.endAt,
  scheduledTime: rx.scheduledTime ? `${rx.scheduledTime}:00` : "09:00:00",
  notes: rx.notes.trim() || null,
});

export default function PrescriptionFields({ value, onChange }) {
  const [suggestions, setSuggestions] = useState([]);
  const [searching, setSearching] = useState(false);
  const [preview, setPreview] = useState(null); // InteractionCheckResponse
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);

  const timerRef = useRef(null);
  const seqRef = useRef(0); // 늦게 도착한 검색 응답이 최신 결과를 덮어쓰지 않게

  const set = (patch) => onChange({ ...value, ...patch });
  const endLocked = value.oneDay || value.openEnded;

  // 미리보기에 쓰는 종료일 (서버 저장 규칙과 같게: 하루 = 시작일, 미정 = 없음)
  const previewEndAt = value.oneDay
    ? value.startAt
    : value.openEnded
      ? undefined
      : value.endAt || undefined;

  useEffect(() => () => clearTimeout(timerRef.current), []);

  // 약 이름 입력 → 250ms 쉬면 검색
  const handleNameChange = (name) => {
    set({ name, medicineId: null }); // 이름을 고치면 선택한 약은 해제
    setSuggestions([]);
    setPreview(null);
    clearTimeout(timerRef.current);

    if (!name.trim()) {
      seqRef.current += 1;
      setSearching(false);
      return;
    }

    setSearching(true);
    const seq = ++seqRef.current;
    timerRef.current = setTimeout(async () => {
      try {
        const results = await searchMedicines(name.trim());
        if (seq === seqRef.current) {
          setSuggestions(Array.isArray(results) ? results : []);
        }
      } catch (error) {
        console.error("약 검색 실패:", error);
        if (seq === seqRef.current) setSuggestions([]);
      } finally {
        if (seq === seqRef.current) setSearching(false);
      }
    }, 250);
  };

  const handleMedicineSelect = (medicine) => {
    seqRef.current += 1; // 진행 중인 검색 응답 무시
    clearTimeout(timerRef.current);
    setSearching(false);
    setSuggestions([]);
    set({ name: medicine.name, medicineId: medicine.medicineId });
  };

  // 약을 고르거나, 기간이 바뀔 때마다 상충 다시 확인
  useEffect(() => {
    if (!value.medicineId || !value.startAt) {
      setPreview(null);
      return;
    }
    let ignore = false;
    setLoadingPreview(true);
    setPreviewFailed(false);
    previewInteractions(
      value.medicineId,
      value.startAt,
      previewEndAt,
      value.prescriptionId,
    )
      .then((res) => {
        if (!ignore) setPreview(res);
      })
      .catch((error) => {
        console.error("상충 확인 실패:", error);
        if (!ignore) {
          setPreview(null);
          setPreviewFailed(true);
        }
      })
      .finally(() => {
        if (!ignore) setLoadingPreview(false);
      });
    return () => {
      ignore = true;
    };
  }, [value.medicineId, value.startAt, previewEndAt, value.prescriptionId]);

  return (
    <>
      <label>
        약 이름
        <input
          autoFocus
          maxLength={30}
          placeholder="예: 타이레놀, 판콜에이"
          value={value.name}
          onChange={(e) => handleNameChange(e.target.value)}
        />
      </label>

      {searching && <p className="interaction-empty">약 검색 중...</p>}

      {!searching && suggestions.length > 0 && (
        <div className="medicine-suggestions">
          {suggestions.map((medicine) => (
            <button
              type="button"
              key={medicine.medicineId}
              onClick={() => handleMedicineSelect(medicine)}
            >
              <strong>{medicine.name}</strong>
              <small>{medicine.kind}</small>
            </button>
          ))}
        </div>
      )}

      {!searching &&
        value.name.trim() &&
        suggestions.length === 0 &&
        !value.medicineId && (
          <p className="interaction-empty">
            목록에 없는 약이에요. 그대로 등록할 수 있지만 영양제와의 상충은
            확인되지 않아요.
          </p>
        )}

      {value.medicineId && (
        <>
          {loadingPreview ? (
            <p className="interaction-empty">상충 정보를 확인하고 있어요...</p>
          ) : previewFailed ? (
            <p className="interaction-empty">상충 정보를 불러오지 못했어요.</p>
          ) : (
            <InteractionWarningList data={preview} />
          )}
        </>
      )}
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
      <label>
        복용 시각
        <input
          type="time"
          value={value.scheduledTime || "09:00"}
          onChange={(e) => set({ scheduledTime: e.target.value })}
        />
      </label>
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
