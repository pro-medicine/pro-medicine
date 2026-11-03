// 오늘 먹은 영양소 (홈, 복용 타임라인 아래) — 복용 체크한 영양제의 성분을 더해 권장량 대비 원형 그래프로 보여준다
// 체크하는 순간 서버 응답을 기다리지 않고 화면의 taken 상태로 다시 계산 → 그래프가 차오르는 애니메이션
import { useEffect, useRef, useState } from "react";

const REDUCED_MOTION =
  typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

// 4.25 → "4.3", 100 → "100", 0.333 → "0.3"
const fmt = (n) => (n >= 10 ? Math.round(n).toLocaleString("ko-KR") : String(Math.round(n * 10) / 10));

// 숫자가 바뀌면 이전 값에서 새 값까지 부드럽게 올라가는 값 (처음에는 0부터)
function useCountUp(target, duration = 700) {
  const [value, setValue] = useState(0);
  const fromRef = useRef(0);
  useEffect(() => {
    if (REDUCED_MOTION) return undefined; // 움직임 줄이기 설정이면 애니메이션 없이 바로 목표값
    const from = fromRef.current;
    let frame;
    let start;
    const step = (time) => {
      start ??= time;
      const t = Math.min((time - start) / duration, 1);
      const eased = 1 - (1 - t) ** 3; // 끝에서 천천히
      const next = from + (target - from) * eased;
      fromRef.current = next;
      setValue(next);
      if (t < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target, duration]);
  return REDUCED_MOTION ? target : value;
}

function NutrientTile({ nutrient, taken }) {
  const percent = Math.round((taken / nutrient.recommended) * 100);
  const shown = useCountUp(percent);
  // 막 늘어났을 때 "+N%" 표시 (줄어든 경우·처음 그릴 때는 표시 안 함)
  // 값이 바뀐 걸 렌더 중에 비교한다 (React 문서의 "props가 바뀔 때 state 조정" 방식)
  const [prevPercent, setPrevPercent] = useState(percent);
  const [delta, setDelta] = useState(null);
  if (percent !== prevPercent) {
    setPrevPercent(percent);
    setDelta(percent > prevPercent ? { diff: percent - prevPercent, key: `${prevPercent}-${percent}` } : null);
  }
  useEffect(() => {
    if (!delta) return undefined;
    const timer = setTimeout(() => setDelta(null), 1400);
    return () => clearTimeout(timer);
  }, [delta]);

  const full = shown >= 99.5; // 그래프가 다 찬 다음에 체크 표시
  return (
    <li
      className={`home-nutri-tile${taken > 0 ? " is-on" : ""}${full ? " is-full" : ""}${delta ? " is-up" : ""}`}
    >
      <span
        className="home-nutri-ring"
        style={{ "--p": Math.min(shown, 100) }}
        role="img"
        aria-label={`${nutrient.name} 권장량의 ${percent}% 먹었어요`}
      >
        <b>
          {Math.round(shown)}
          <small>%</small>
        </b>
        {full && (
          <i className="home-nutri-check" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m5 12 4 4L19 6" />
            </svg>
          </i>
        )}
      </span>
      {delta && (
        <em className="home-nutri-delta" key={delta.key} aria-hidden="true">
          +{delta.diff}%
        </em>
      )}
      <strong>{nutrient.name}</strong>
      <small>
        {fmt(taken)}
        {nutrient.unit} / {fmt(nutrient.recommended)}
        {nutrient.unit}
      </small>
    </li>
  );
}

/**
 * props
 * - nutrition: GET /users/{id}/nutrition/today 응답 { nutrients, schedules, unsupportedCount, sample }
 * - supplements: 홈 타임라인 목록 (id = scheduleId, taken = 복용 체크 여부)
 */
export default function TodayNutrients({ nutrition, supplements }) {
  if (!nutrition?.nutrients?.length) return null;

  const takenIds = new Set(supplements.filter((s) => s.taken).map((s) => s.id));
  const totals = {};
  for (const schedule of nutrition.schedules ?? []) {
    if (!takenIds.has(schedule.scheduleId)) continue;
    for (const [code, amount] of Object.entries(schedule.amounts ?? {})) {
      totals[code] = (totals[code] ?? 0) + amount;
    }
  }
  const takenCount = takenIds.size;

  return (
    <section className="home-nutri" aria-labelledby="home-nutri-title">
      <div className="home-card-head">
        <h3 className="home-card-title" id="home-nutri-title">
          오늘 먹은 영양소
        </h3>
        <span className="home-nutri-sub">
          {takenCount > 0 ? `체크한 영양제 ${takenCount}개 기준` : "권장량 대비"}
        </span>
      </div>

      {takenCount === 0 && <p className="home-nutri-empty">복용 체크를 하면 먹은 만큼 그래프가 채워져요</p>}

      {/* 순서는 고정 (값이 바뀌어도 타일이 움직이지 않게) */}
      <ul className="home-nutri-tiles">
        {nutrition.nutrients.map((n) => (
          <NutrientTile key={n.code} nutrient={n} taken={totals[n.code] ?? 0} />
        ))}
      </ul>

      <p className="home-nutri-foot">
        권장량은 한국인 영양소 섭취기준(성인)이에요. 음식으로 먹은 양은 들어가지 않아요.
        {nutrition.unsupportedCount > 0 && ` 함량 정보가 없는 영양제 ${nutrition.unsupportedCount}개는 빠졌어요.`}
        {nutrition.sample && " 일부 함량은 예시 데이터예요."}
      </p>
    </section>
  );
}
