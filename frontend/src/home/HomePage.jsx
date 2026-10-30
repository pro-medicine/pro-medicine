import { useEffect, useRef, useState } from "react";
import { getHome, updateIntake } from "./homeApi";
import HomeHeader from "./HomeHeader";
import PromiseCard from "./PromiseCard";
import TodaySupplements from "./TodaySupplements";
import RecommendBanner from "./RecommendBanner";
import IntakeAlarmCard from "./IntakeAlarmCard";
import "./home.css";

// 홈 화면 (담당: 채원) — 데이터 불러오기 + 복용 체크 + 화면 조립
export default function HomePage({
                                   userId,
                                   onOpenNotifications,
                                   onSurvey,
                                   headerActions,
                                   refreshKey,
                                 }) {
  const [home, setHome] = useState(null);
  const [isSample, setIsSample] = useState(false);
  const [error, setError] = useState("");
  const [pendingIds, setPendingIds] = useState([]);
  const pendingRef = useRef(new Set());

  // 복용 체크 저장 성공 시 알림 카드를 다시 조회하기 위한 값
  const [intakeSavedKey, setIntakeSavedKey] = useState(0);

  // 홈 데이터 조회
  useEffect(() => {
    let ignore = false;

    getHome(userId).then(({ data, isSample }) => {
      if (ignore) return;

      setHome(data);
      setIsSample(isSample);
    });

    return () => {
      ignore = true;
    };
  }, [userId, refreshKey]);

  // 데이터 로딩
  if (!home) {
    return (
        <div className="home-page home-loading">
          불러오는 중...
        </div>
    );
  }

  // 복용 진행률 계산
  const supplements = home.supplements ?? [];
  const total = supplements.length;
  const done = supplements.filter((s) => s.taken).length;
  const percent =
      total === 0 ? 0 : Math.round((done / total) * 100);

  // 특정 영양제의 복용 상태 변경
  const setTaken = (id, taken) => {
    setHome((prev) => ({
      ...prev,
      supplements: prev.supplements.map((s) =>
          s.id === id ? { ...s, taken } : s
      ),
    }));
  };

  // 복용 체크 처리
  const handleToggle = async (id) => {
    // 중복 요청 방지
    if (pendingRef.current.has(id)) return;

    const next = !supplements.find((s) => s.id === id).taken;

    // UI 먼저 반영
    setTaken(id, next);
    setError("");

    // 예시 데이터일 경우 서버에 저장하지 않음
    if (isSample) return;

    pendingRef.current.add(id);
    setPendingIds((prev) => [...prev, id]);

    try {
      // 로그인 사용자 ID와 현재 표시 중인 날짜로 복용 기록 저장
      await updateIntake(userId, id, next, home.today);

      // 저장 성공 시 복용 알림 카드 갱신
      setIntakeSavedKey((key) => key + 1);
    } catch (e) {
      // 저장 실패 시 이전 상태로 복구
      setTaken(id, !next);

      setError(
          "복용 체크를 저장하지 못했어요. 잠시 후 다시 시도해주세요."
      );
    } finally {
      pendingRef.current.delete(id);
      setPendingIds((prev) => prev.filter((p) => p !== id));
    }
  };

  return (
      <div className="home-page">
        <div className="home-inner">

          {/* 홈 헤더: 사용자 정보, 로그아웃, 알림 */}
          <HomeHeader
              userName={home.userName}
              today={home.today}
              hasUnread={home.hasUnreadNotifications}
              onBellClick={onOpenNotifications}
              headerActions={headerActions}
          />

          {/* 예시 데이터 안내 */}
          {isSample && (
              <p className="home-sample-notice">
                예시 데이터로 표시 중입니다
              </p>
          )}

          {/* 오늘의 복용 진행률 */}
          <PromiseCard
              percent={percent}
              remain={total - done}
              total={total}
          />

          {/* 복용 알림 카드 - develop 신규 기능 */}
          <IntakeAlarmCard
              userId={userId}
              refreshKey={`${refreshKey}-${intakeSavedKey}`}
              isSample={isSample}
          />

          {/* 오늘의 영양제 복용 목록 */}
          <TodaySupplements
              supplements={supplements}
              done={done}
              total={total}
              pendingIds={pendingIds}
              onToggle={handleToggle}
          />

          {error && (
              <p className="home-error">
                {error}
              </p>
          )}

          {/* 맞춤 영양제 추천 설문 */}
          <RecommendBanner onClick={onSurvey} />

        </div>
      </div>
  );
}
