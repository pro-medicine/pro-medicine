// 홈 화면 (담당: 채원) — 데이터 불러오기 + 복용 체크 + 화면 조립
import { getHome, updateIntake } from "./homeApi";
import HomeHeader from "./HomeHeader";
import PromiseCard from "./PromiseCard";
import TodaySupplements from "./TodaySupplements";
import RecommendBanner from "./RecommendBanner";
import "./home.css";
import { useEffect, useRef, useState } from "react";

/**
 * props (App.jsx에서 전달)
 * - userId: 로그인 사용자 id
 * - onOpenNotifications: 종 버튼 클릭 시 알림 패널 열기
 * * - refreshKey: 값이 바뀌면 홈 데이터를 다시 불러옴 (알림 패널을 닫을 때 → 종 아이콘 빨간 점 갱신)
 */
export default function HomePage({
  userId = 1,
  onOpenNotifications,
  onSurvey, refreshKey,

}) {
  const [home, setHome] = useState(null); // 홈 데이터
  const [isSample, setIsSample] = useState(false); // 예시 데이터 사용 여부
  const [error, setError] = useState(""); // 저장 실패 메시지
  const [pendingIds, setPendingIds] = useState([]); // 저장 중인 카드 id (화면 비활성화용)
  const pendingRef = useRef(new Set()); // 연속 클릭을 즉시 막기 위한 기록

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

  // 2) 데이터 오기 전에는 로딩 화면
  if (!home) {
    return <div className="home-page home-loading">불러오는 중...</div>;
  }

  // 3) 진행률 계산 (state로 저장하지 않고 매번 계산)
  const supplements = home.supplements ?? [];
  const total = supplements.length;
  const done = supplements.filter((s) => s.taken).length;
  const percent = total === 0 ? 0 : Math.round((done / total) * 100);

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

    const next = !supplements.find((s) => s.id === id).taken;
    setTaken(id, next);
    setError("");

    if (isSample) return; // 예시 데이터일 때는 서버 저장 안 함

    pendingRef.current.add(id);
    setPendingIds((prev) => [...prev, id]);

    try {
      await updateIntake(userId, id, next);
    } catch (e) {
      setTaken(id, !next);
      setError("복용 체크를 저장하지 못했어요. 잠시 후 다시 시도해주세요.", e);
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

        <PromiseCard percent={percent} remain={total - done} total={total} />

        <TodaySupplements
          supplements={supplements}
          done={done}
          total={total}
          pendingIds={pendingIds}
          onToggle={handleToggle}
        />
        {error && <p className="home-error">{error}</p>}

        {/* TODO: 추천 설문 모달 만들면 연결 */}
        <RecommendBanner onClick={onSurvey} />
      </div>
    </div>
  );
}
