// 홈 화면 (담당: 채원) — 데이터 불러오기 + 복용 체크 + 화면 조립
import { getHome, getStreakDays, updateIntake } from "./homeApi";
import HomeHeader from "./HomeHeader";
import PromiseCard from "./PromiseCard";
import TodaySupplements from "./TodaySupplements";
import RecommendBanner from "./RecommendBanner";
import IntakeAlarmCard from "./IntakeAlarmCard";
import { AllDoneCard, EmptySupplements, HomeSkeleton, HomeToast } from "./HomeStates";
import "./home.css";
import { useEffect, useRef, useState } from "react";

/**
 * props (App.jsx에서 전달)
 * - userId: 로그인 사용자 id
 * - onOpenNotifications: 종 버튼 클릭 시 알림 패널 열기
 * * - refreshKey: 값이 바뀌면 홈 데이터를 다시 불러옴 (알림 패널을 닫을 때 → 종 아이콘 빨간 점 갱신)
 * - onAddSupplement: 영양제가 없을 때 "영양제 등록하기" → 마이페이지 영양제 등록 화면으로
 */
export default function HomePage({
  userId = 1,
  onOpenNotifications,
  onSurvey,
  refreshKey,
  onAddSupplement,
}) {
  const [home, setHome] = useState(null); // 홈 데이터
  const [isSample, setIsSample] = useState(false); // 예시 데이터 사용 여부
  const [toast, setToast] = useState(null); // 체크 결과 토스트 { type, text, actionLabel, id }
  const [streakDays, setStreakDays] = useState(0); // 연속 복용 일수 ("N일 연속 복용 중" 배지)
  const [pendingIds, setPendingIds] = useState([]); // 저장 중인 카드 id (화면 비활성화용)
  const pendingRef = useRef(new Set()); // 연속 클릭을 즉시 막기 위한 기록
  const [intakeSavedKey, setIntakeSavedKey] = useState(0); // 복용 체크 저장 성공 → 복용 알림 카드 다시 불러오기

  // 1) 화면이 처음 열릴 때 GET /home 호출 (알림 패널을 닫을 때도 다시 호출)
  useEffect(() => {
    let ignore = false; // 다시 불러올 때 늦게 도착한 이전 응답은 버린다
    getHome(userId).then(({ data, isSample }) => {
      if (ignore) return;
      setHome(data);
      setIsSample(isSample);
    });
    return () => {
      ignore = true;
    };
  }, [userId, refreshKey]);

  // 연속 복용 일수: 홈을 불러올 때와 복용 체크 저장 후 다시 조회 (예시 데이터면 예시 값 사용)
  useEffect(() => {
    if (isSample) return undefined;
    let ignore = false;
    getStreakDays(userId)
      .then((days) => {
        if (!ignore) setStreakDays(days);
      })
      .catch(() => {
        if (!ignore) setStreakDays(0); // 못 불러오면 배지만 숨김
      });
    return () => {
      ignore = true;
    };
  }, [userId, refreshKey, intakeSavedKey, isSample]);

  // 토스트는 4초 뒤 자동으로 닫힘
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  // 2) 데이터 오기 전에는 로딩 화면 (스켈레톤)
  if (!home) {
    return <HomeSkeleton />;
  }

  // 3) 진행률 계산 (state로 저장하지 않고 매번 계산)
  const supplements = home.supplements ?? [];
  const total = supplements.length;
  const done = supplements.filter((s) => s.taken).length;
  const percent = total === 0 ? 0 : Math.round((done / total) * 100);
  const shownStreak = isSample ? (home.streakDays ?? 0) : streakDays;

  // 특정 영양제의 taken 값만 바꾸기
  const setTaken = (id, taken) => {
    setHome((prev) => ({
      ...prev,
      supplements: prev.supplements.map((s) =>
        s.id === id ? { ...s, taken } : s,
      ),
    }));
  };

  // 4) 복용 체크: 화면 먼저 바꾸고 → 서버 저장 실패하면 되돌리기
  //    저장 중인 카드는 다시 못 누르게 해서, 늦게 끝난 요청이 최신 상태를 덮어쓰지 않게 한다.
  const handleToggle = async (id) => {
    if (pendingRef.current.has(id)) return; // 이 카드는 저장 중 → 클릭 무시

    const item = supplements.find((s) => s.id === id);
    const next = !item.taken;
    setTaken(id, next);
    // 성공 토스트: 잘못 눌렀으면 "되돌리기"로 바로 취소 (= 한 번 더 토글)
    const doneToast = {
      type: "success",
      text: next ? `${item.name} 복용을 기록했어요` : `${item.name} 체크를 해제했어요`,
      actionLabel: "되돌리기",
      id,
    };

    if (isSample) {
      setToast(doneToast); // 예시 데이터일 때는 서버 저장 안 함
      return;
    }

    pendingRef.current.add(id);
    setPendingIds((prev) => [...prev, id]);

    try {
      await updateIntake(userId, id, next, home.today); // 화면에 표시 중인 날짜로 저장
      setIntakeSavedKey((k) => k + 1);
      setToast(doneToast);
    } catch (e) {
      console.warn("[home] 복용 체크 저장 실패", e);
      setTaken(id, !next);
      setToast({ type: "error", text: "저장하지 못했어요. 다시 시도해 주세요", actionLabel: "재시도", id });
    } finally {
      // 성공이든 실패든 끝나면 다시 누를 수 있게
      pendingRef.current.delete(id);
      setPendingIds((prev) => prev.filter((p) => p !== id));
    }
  };

  return (
    <div className="home-page">
      <div className="home-inner">
        <HomeHeader
          userName={home.userName}
          today={home.today}
          hasUnread={home.hasUnreadNotifications}
          onBellClick={onOpenNotifications}
        />

        {isSample && (
          <p className="home-sample-notice">예시 데이터로 표시 중입니다</p>
        )}

        {/* 넓은 화면: 왼쪽(오늘 먹을 영양제·추천 배너) + 오른쪽(오늘의 약속·복용 알림) */}
        <div className="home-columns">
          <div className="home-main">
            {total > 0 && done === total && <AllDoneCard streakDays={shownStreak} />}
            {total === 0 && !isSample ? (
              <EmptySupplements onAdd={onAddSupplement} />
            ) : (
              <TodaySupplements
                supplements={supplements}
                done={done}
                total={total}
                pendingIds={pendingIds}
                onToggle={handleToggle}
                today={home.today}
              />
            )}
            {/* TODO: 추천 설문 모달 만들면 연결 */}
            <RecommendBanner onClick={onSurvey} />
          </div>

          <aside className="home-side">
            <PromiseCard
              percent={percent}
              remain={total - done}
              total={total}
              streakDays={shownStreak}
            />
            {/* 다음 알림은 타임라인에 보이므로 카드에서는 이번 주 제시간 복용률만 */}
            <IntakeAlarmCard
              userId={userId}
              refreshKey={`${refreshKey}-${intakeSavedKey}`}
              isSample={isSample}
              showNext={false}
            />
          </aside>
        </div>
      </div>

      <HomeToast
        toast={toast}
        onAction={() => {
          const id = toast?.id;
          setToast(null);
          if (id != null) handleToggle(id); // 되돌리기 / 재시도 모두 한 번 더 토글
        }}
        onClose={() => setToast(null)}
      />
    </div>
  );
}
