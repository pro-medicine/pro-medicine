import { useState, useRef, useEffect } from "react";
import {
  addMySupplement,
  searchSupplements,
  updateSupplementSchedules,
  fetchIntakeTips,
} from "../api/mypageApi";
import { useModalFocus } from "../common/useModalFocus";
import "../home/home.css";

// 끼니별 기본 알림 시각
const mealSlotOptions = [
  { slot: "BREAKFAST", label: "아침", defaultTime: "08:00" },
  { slot: "LUNCH", label: "점심", defaultTime: "12:30" },
  { slot: "DINNER", label: "저녁", defaultTime: "18:30" },
];
const mealRelationOptions = [
  ["AFTER", "식후"],
  ["BEFORE", "식전"],
  ["ANY", "상관없음"],
];

function initialSchedules(savedSchedules) {
  const result = {};
  for (const option of mealSlotOptions) {
    if (!savedSchedules) {
      result[option.slot] = {
        checked: option.slot === "BREAKFAST",
        relation: "AFTER",
        time: option.defaultTime,
      };
      continue;
    }
    const saved = savedSchedules.find((s) => s.mealSlot === option.slot);
    result[option.slot] = {
      checked: !!saved,
      relation: saved ? saved.mealRelation : "AFTER",
      time: saved ? saved.remindAt.slice(0, 5) : option.defaultTime,
    };
  }
  return result;
}

function SupplementModal({ title, onClose, openerRef, children }) {
  const panelRef = useModalFocus(onClose, openerRef);

  return (
    <div className="supplement-add-overlay">
      <section
        ref={panelRef}
        className="supplement-add-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="supplement-add-title"
        tabIndex={-1}
      >
        <div className="supplement-add-header">
          <h2 id="supplement-add-title">{title}</h2>
          <button type="button" aria-label={`${title} 닫기`} onClick={onClose}>
            ✕
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

export default function SupplementAddPage({
  userId,
  target,
  openerRef,
  onBack,
  onSaved,
  panelInstanceId,
}) {
  const isEdit = !!target;
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState(null);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState(null);
  const [manualMode, setManualMode] = useState(false);
  const [manualName, setManualName] = useState("");
  const [schedules, setSchedules] = useState(() =>
    initialSchedules(target?.schedules),
  );
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);

  const [tips, setTips] = useState([]);
  const [tipError, setTipError] = useState(false);
  const tipSeq = useRef(0);

  const loadTips = async (supplementId) => {
    const seq = ++tipSeq.current;
    setTips([]);
    setTipError(false);
    if (!supplementId) return;
    try {
      const list = await fetchIntakeTips(supplementId);
      if (seq === tipSeq.current) {
        setTips(Array.isArray(list) ? list : []);
      }
    } catch {
      if (seq === tipSeq.current) {
        setTips([]);
        setTipError(true);
      }
    }
  };

  const clearTips = () => {
    tipSeq.current += 1;
    setTips([]);
    setTipError(false);
  };

  const activeSupplementId = isEdit
    ? target?.supplementId
    : selected?.supplementId;

  useEffect(() => {
    if (isEdit && target?.supplementId) loadTips(target.supplementId);
    const seq = ++tipSeq.current;
    fetchIntakeTips(target.catalogProductId)
      .then((list) => {
        if (seq === tipSeq.current) setTips(Array.isArray(list) ? list : []);
      })
      .catch(() => {
        if (seq === tipSeq.current) setTipError(true);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const search = async () => {
    if (searching) return;
    const trimmed = keyword.trim();
    if (trimmed === "") {
      setMessage("제품명을 입력해 주세요.");
      return;
    }
    setSearching(true);
    setMessage(null);
    setSelected(null);
    clearTips();
    try {
      // 엑셀 영양제(성분 함량 데이터가 있는 목록)에서 검색
      setResults(await searchSupplements(trimmed));
    } catch (e) {
      setResults(null);
      setMessage(e.message || "검색하지 못했어요.");
    } finally {
      setSearching(false);
    }
  };

  const changeSchedule = (slot, key, value) => {
    setSchedules((prev) => ({
      ...prev,
      [slot]: { ...prev[slot], [key]: value },
    }));
    setMessage(null);
  };

  const submit = async () => {
    if (userId == null) {
      setMessage("로그인 사용자 정보를 확인할 수 없어요.");
      return;
    }

    const trimmedName = manualName.trim();
    if (!isEdit && manualMode && trimmedName === "") {
      setMessage("영양제 이름을 입력해 주세요.");
      return;
    }
    if (!isEdit && !manualMode && !selected) {
      setMessage("등록할 제품을 골라 주세요.");
      return;
    }

    const picked = mealSlotOptions
      .filter((option) => schedules[option.slot].checked)
      .map((option) => ({
        mealSlot: option.slot,
        mealRelation: schedules[option.slot].relation,
        remindAt: schedules[option.slot].time,
      }));
    if (!isEdit && picked.length === 0) {
      setMessage("복용할 끼니를 하나 이상 골라 주세요.");
      return;
    }
    if (picked.some((schedule) => schedule.remindAt === "")) {
      setMessage("알림 시각을 입력해 주세요.");
      return;
    }

    setSaving(true);
    setMessage(null);
    try {
      if (isEdit) {
        await updateSupplementSchedules(target.userProductId, picked);
      } else {
        await addMySupplement(
          manualMode
            ? { manualProductName: trimmedName, schedules: picked }
            : {
                supplementId: selected.supplementId,
                schedules: picked,
              },
        );
      }
      // 저장한 영양제 이름 (마이페이지 토스트 문구용)
      let savedName = trimmedName;
      if (isEdit) savedName = target.productName;
      else if (!manualMode) savedName = selected.productName;
      onSaved(panelInstanceId, savedName);
    } catch (e) {
      setMessage(e.message || "등록하지 못했어요.");
      setSaving(false);
    }
  };

  return (
    <SupplementModal
      title={isEdit ? "복용 일정 수정" : "영양제 등록"}
      onClose={() => onBack(panelInstanceId)}
      openerRef={openerRef}
    >
      <div className="profile-edit">
        {isEdit && (
          <section className="profile-edit-card">
            <h2>{target.productName}</h2>
            <p className="profile-edit-hint">
              {target.manufacturer ?? "제조사 정보 없음"} · 제품을 바꾸려면 삭제
              후 다시 등록해 주세요.
            </p>
          </section>
        )}

        {!isEdit && (
          <section className="profile-edit-card">
            <h2>
              <span className="step-num">1</span>제품 검색
            </h2>
            <div className="supplement-search-row">
              <input
                value={keyword}
                maxLength={50}
                placeholder="제품명을 입력해 주세요 (예: 비타민)"
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    search();
                  }
                }}
              />
              <button
                type="button"
                className="btn btn-dark"
                onClick={search}
                disabled={searching}
              >
                {searching ? "검색 중..." : "검색"}
              </button>
              <button
                type="button"
                className="btn btn-outline"
                aria-pressed={manualMode}
                onClick={() => {
                  setManualMode((prev) => !prev);
                  setSelected(null);
                  clearTips();
                  setMessage(null);
                }}
              >
                직접 입력
              </button>
            </div>

            {results && results.length === 0 && (
              <p className="profile-edit-hint">검색 결과가 없어요.</p>
            )}
            {results && results.length > 0 && (
              <ul className="supplement-search-results">
                {results.map((product) => (
                  <li key={product.supplementId}>
                    <button
                      type="button"
                      className={
                        selected?.supplementId === product.supplementId
                          ? "supplement-search-item selected"
                          : "supplement-search-item"
                      }
                      onClick={() => {
                        setSelected(product);
                        setManualMode(false);
                        loadTips(product.supplementId);
                        setMessage(null);
                      }}
                    >
                      <strong>{product.productName}</strong>
                      <span>{product.manufacturer ?? "제조사 정보 없음"}</span>
                      {product.intakeMethod && (
                        <small>{product.intakeMethod}</small>
                      )}
                      {product.nutrientSummary && (
                        <small>하루 {product.nutrientSummary}</small>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}

        {!isEdit && manualMode && (
          <section className="profile-edit-card">
            <h2>직접 입력</h2>
            <input
              className="supplement-manual-input"
              value={manualName}
              maxLength={100}
              placeholder="영양제 이름을 입력해 주세요"
              aria-label="영양제 이름"
              autoFocus
              onChange={(event) => {
                setManualName(event.target.value);
                setMessage(null);
              }}
            />
            <p className="profile-edit-hint">
              직접 입력한 영양제는 함량 정보가 없어 영양 관리 계산에서 빠져요.
            </p>
          </section>
        )}
        <section className="profile-edit-card">
          <h2>{!isEdit && <span className="step-num">2</span>}복용 일정</h2>
          <p className="profile-edit-hint">
            먹는 끼니를 고르면 그 시간에 알림을 보내드려요.
          </p>
          {tips.length > 0 && (
            <div className="supplement-intake-tips">
              <ul>
                {tips.map((tip, index) => (
                  <li key={tip.ingredient || index}>{tip.message}</li>
                ))}
              </ul>
              <small>
                제품에 적힌 섭취 방법과 의사·약사 안내를 먼저 따라 주세요.
              </small>
            </div>
          )}
          {tipError && (
            <div className="supplement-intake-tips error">
              <p className="profile-edit-hint error">
                섭취 안내 정보를 불러오지 못했어요.
              </p>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => loadTips(activeSupplementId)}
              >
                다시 시도
              </button>
            </div>
          )}

          {mealSlotOptions.map((option) => {
            const schedule = schedules[option.slot];
            return (
              <div
                className={
                  schedule.checked
                    ? "supplement-schedule-row checked"
                    : "supplement-schedule-row"
                }
                key={option.slot}
              >
                <label className="supplement-schedule-check">
                  <input
                    type="checkbox"
                    checked={schedule.checked}
                    onChange={(event) =>
                      changeSchedule(
                        option.slot,
                        "checked",
                        event.target.checked,
                      )
                    }
                  />
                  {option.label}
                </label>
                {schedule.checked && (
                  <>
                    <div
                      className="supplement-relation"
                      role="radiogroup"
                      aria-label={`${option.label} 식전·식후`}
                    >
                      {mealRelationOptions.map(([value, text]) => (
                        <button
                          key={value}
                          type="button"
                          role="radio"
                          aria-checked={schedule.relation === value}
                          className={
                            schedule.relation === value
                              ? "choice active"
                              : "choice"
                          }
                          onClick={() =>
                            changeSchedule(option.slot, "relation", value)
                          }
                        >
                          {text}
                        </button>
                      ))}
                    </div>
                    <input
                      type="time"
                      value={schedule.time}
                      aria-label={`${option.label} 알림 시각`}
                      onChange={(event) =>
                        changeSchedule(option.slot, "time", event.target.value)
                      }
                    />
                  </>
                )}
              </div>
            );
          })}
        </section>

        {message && <p className="profile-edit-message error">{message}</p>}

        <div className="profile-edit-footer">
          <button
            type="button"
            className="btn btn-outline btn-lg"
            onClick={() => onBack(panelInstanceId)}
          >
            취소
          </button>
          <button
            type="button"
            className="btn btn-primary btn-lg"
            onClick={submit}
            disabled={saving}
          >
            {saving ? "저장 중..." : isEdit ? "저장" : "등록"}
          </button>
        </div>
      </div>
    </SupplementModal>
  );
}
