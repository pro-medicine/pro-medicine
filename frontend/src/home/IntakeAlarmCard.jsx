// 홈 복용 알림 카드 (담당: 응진) — 다음 복용 알림 + 이번 주 제시간 복용률
// API: GET /api/v1/notifications/intake-summary (알림 도메인)
import { useEffect, useState } from "react";
import { getIntakeSummary } from "./homeApi";
import "./intakeAlarm.css";

const MEAL_LABEL = { BREAKFAST: "아침", LUNCH: "점심", DINNER: "저녁" };

// ⚠️ 예시 데이터 (실제 정보 아님): 홈이 예시 데이터로 표시될 때만 사용
const SAMPLE_SUMMARY = {
  nextReminder: { remindAt: "12:30:00", mealSlot: "LUNCH", count: 1, names: ["초임계 알티지 오메가3"] },
  onTime: { total: 10, onTime: 8, rate: 80 },
};

// "12:30:00" → 오늘 그 시각까지 남은 밀리초 (지났으면 0 이하)
function msUntil(remindAt, now = new Date()) {
  const [h, m] = remindAt.split(":").map(Number);
  const target = new Date(now);
  target.setHours(h, m, 0, 0);
  return target - now;
}

// 남은 시간 문구: "35분 뒤", "2시간 뒤", "2시간 10분 뒤"
function remainText(remindAt, now) {
  const minutes = Math.ceil(msUntil(remindAt, now) / 60000);
  if (minutes <= 0) return "곧 알림";
  if (minutes < 60) return `${minutes}분 뒤`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours}시간 뒤` : `${hours}시간 ${rest}분 뒤`;
}

// 영양제 이름: 1개면 그대로, 여러 개면 "오메가3 외 1개"
function namesText(names = []) {
  if (names.length <= 1) return names[0] ?? "";
  return `${names[0]} 외 ${names.length - 1}개`;
}

/**
 * props (HomePage에서 전달)
 * - userId: 로그인 사용자 id
 * - refreshKey: 값이 바뀌면 다시 불러옴 (복용 체크 저장 후, 알림 패널을 닫을 때)
 * - isSample: 홈이 예시 데이터로 표시 중이면 API를 부르지 않고 예시 카드 표시
 */
export default function IntakeAlarmCard({ userId, refreshKey, isSample }) {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => new Date()); // "N분 뒤" 문구를 1분마다 갱신
  const [reloadKey, setReloadKey] = useState(0); // 다음 알림 시각이 지나면 다시 불러오기

  // 1) 카드 데이터 불러오기
  useEffect(() => {
    if (isSample) return undefined;
    let ignore = false; // 다시 불러올 때 늦게 도착한 이전 응답은 버린다
    let timer;
    getIntakeSummary(userId)
      .then((data) => {
        if (ignore) return;
        setSummary(data);
        setError("");
        // 다음 알림 시각이 지나면 그다음 알림으로 바뀌어야 하므로, 그 시각에 맞춰 한 번 더 불러온다
        const next = data?.nextReminder;
        if (next) {
          const wait = msUntil(next.remindAt) + 1000;
          if (wait > 0) timer = setTimeout(() => setReloadKey((k) => k + 1), wait);
        }
      })
      .catch((e) => {
        if (ignore) return;
        console.warn("[home] 복용 알림 카드 호출 실패", e);
        setError("복용 알림 정보를 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
      clearTimeout(timer);
    };
  }, [userId, refreshKey, reloadKey, isSample]);

  // 2) 남은 시간 문구를 1분마다 갱신
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const data = isSample ? SAMPLE_SUMMARY : summary;
  const next = data?.nextReminder;
  const onTime = data?.onTime;

  return (
    <section className="home-alarm" aria-label="복용 알림">
      <div className="home-section-head">
        <div>
          <p className="home-eyebrow">REMINDER</p>
          <h3 className="home-section-title">복용 알림</h3>
        </div>
      </div>

      {error && <p className="home-alarm-message">{error}</p>}
      {!error && !data && <p className="home-alarm-message">불러오는 중...</p>}

      {!error && data && (
        <div className="home-alarm-card">
          {/* 다음 알림 */}
          <div className="home-alarm-block">
            <p className="home-alarm-label">다음 알림</p>
            {next ? (
              <>
                <div className="home-alarm-time-row">
                  <strong className="home-alarm-time">{next.remindAt.slice(0, 5)}</strong>
                  <span className="home-alarm-chip">{remainText(next.remindAt, now)}</span>
                </div>
                <p className="home-alarm-sub">
                  {MEAL_LABEL[next.mealSlot] ?? ""} 영양제 {next.count}개 · {namesText(next.names)}
                </p>
              </>
            ) : (
              <>
                <strong className="home-alarm-empty">오늘 남은 알림이 없어요</strong>
                <p className="home-alarm-sub">오늘 복용 알림을 모두 마쳤어요</p>
              </>
            )}
          </div>

          {/* 이번 주 제시간 복용률 */}
          <div className="home-alarm-block">
            <p className="home-alarm-label">이번 주 제시간 복용</p>
            <strong className="home-alarm-time">
              {onTime.rate}
              <span className="home-alarm-unit">%</span>
            </strong>
            <div
              className="home-alarm-bar"
              role="img"
              aria-label={`이번 주 제시간 복용률 ${onTime.rate}%`}
            >
              <span style={{ width: `${onTime.rate}%` }} />
            </div>
            <p className="home-alarm-sub">
              {onTime.total > 0
                ? `알림 후 1시간 안에 챙긴 비율 · ${onTime.total}번 중 ${onTime.onTime}번`
                : "이번 주 복용 기록이 아직 없어요"}
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
