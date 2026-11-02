import { useState } from "react";
import {
  addMySupplement,
  searchCatalogProducts,
  updateSupplementSchedules,
} from "../api/mypageApi";
import { useModalFocus } from "../common/useModalFocus";

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

// 등록: 아침 식후 08:00 만 체크된 상태
// 수정: 지금 저장된 일정대로 체크 (remindAt "08:00:00" → "08:00")
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

// 영양제 등록·복용 일정 수정 모달 틀 (Esc 닫기, Tab 포커스 가두기, 닫으면 여는 버튼으로 포커스 복원)
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

// 영양제 등록 모달 (마이페이지 "영양제 등록" 버튼)
// 흐름: 제품 검색 → 제품 선택(또는 직접 입력) → 복용 일정 선택 → 등록 → 모달 닫힘
// target(내 영양제 1개)을 받으면 수정 모드: 제품은 고정, 복용 일정만 바꿈 (카드의 연필 버튼)
export default function SupplementAddPage({
  userId,
  target,
  openerRef, // 모달을 연 버튼 (닫을 때 포커스 복원)
  onBack,
  onSaved,
}) {
  const isEdit = !!target;
  const [keyword, setKeyword] = useState("");
  // 검색 결과, null 이면 아직 검색 안 함
  const [results, setResults] = useState(null);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState(null);
  // 직접 입력: 검색해도 안 나오는 제품을 이름으로 등록
  const [manualMode, setManualMode] = useState(false);
  const [manualName, setManualName] = useState("");
  // 끼니별 { checked, relation, time }
  const [schedules, setSchedules] = useState(() =>
    initialSchedules(target?.schedules),
  );
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);

  const search = async () => {
    // 검색 중이면 새 요청을 보내지 않는다 (Enter 연타로 요청이 겹치는 것 방지)
    if (searching) return;
    const trimmed = keyword.trim();
    if (trimmed === "") {
      setMessage("제품명을 입력해 주세요.");
      return;
    }
    setSearching(true);
    setMessage(null);
    setSelected(null);
    try {
      setResults(await searchCatalogProducts(trimmed));
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
    // 로그인 사용자 ID 확인
    if (userId == null) {
      setMessage("로그인 사용자 정보를 확인할 수 없어요.");
      return;
    }

    // 직접 입력이면 이름, 아니면 검색에서 고른 제품이 있어야 함
    const trimmedName = manualName.trim();
    if (!isEdit && manualMode && trimmedName === "") {
      setMessage("영양제 이름을 입력해 주세요.");
      return;
    }
    if (!isEdit && !manualMode && !selected) {
      setMessage("등록할 제품을 골라 주세요.");
      return;
    }
    // 체크한 끼니만 서버로 보냄
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
        await updateSupplementSchedules(userId, target.userProductId, picked);
      } else {
        await addMySupplement(
          userId,
          manualMode
            ? { manualProductName: trimmedName, schedules: picked }
            : {
                catalogProductId: selected.catalogProductId,
                schedules: picked,
              },
        );
      }
      onSaved(); // 성공하면 마이페이지로 돌아가므로 saving 은 되돌리지 않음
    } catch (e) {
      // 예: 이미 등록한 영양제입니다.
      setMessage(e.message || "등록하지 못했어요.");
      setSaving(false);
    }
  };

  return (
    <SupplementModal
      title={isEdit ? "복용 일정 수정" : "영양제 등록"}
      onClose={onBack}
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
                  setSelected(null); // 직접 입력과 검색 선택은 둘 중 하나만
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
                  <li key={product.catalogProductId}>
                    <button
                      type="button"
                      className={
                        selected?.catalogProductId === product.catalogProductId
                          ? "supplement-search-item selected"
                          : "supplement-search-item"
                      }
                      onClick={() => {
                        setSelected(product);
                        setManualMode(false); // 검색 결과를 고르면 직접 입력은 끔
                        setMessage(null);
                      }}
                    >
                      <strong>{product.productName}</strong>
                      <span>{product.manufacturer ?? "제조사 정보 없음"}</span>
                      {product.intakeMethod && (
                        <small>{product.intakeMethod}</small>
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
                {/* 체크한 끼니만 식전·식후와 알림 시각을 고른다 */}
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
            onClick={onBack}
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
