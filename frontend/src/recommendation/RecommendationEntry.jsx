// 추천 진입점 (담당: 영준) — 홈 배너에서 열리면 비회원은 기본 설문(V1), 회원은 맞춤 질문(V2) 또는 기본 설문으로 나눈다
import PersonalizedModal from "./PersonalizedModal";
import RecommendationModal from "./RecommendationModal";
import { useCallback, useState } from "react";

/**
 * props (App.jsx에서 전달)
 * - userId: 로그인 사용자 id (없으면 비회원). TODO: 로그인 연동 후 App.jsx의 userId만 실제 값으로 바뀌면 된다
 * - onClose: 모달 닫기
 *
 * 회원이면 PersonalizedModal이 모드 API로 확인하고, 건강정보가 부족하면 onFallbackToV1으로 기본 설문(회원 모드)으로 바꾼다.
 * 기본 설문을 마친 회원이 맞춤 질문 조건을 갖추면 결과 화면에서 맞춤 질문으로 넘어갈 수 있다.
 */
export default function RecommendationEntry({ userId, onClose }) {
  const [mode, setMode] = useState(userId ? "V2" : "V1"); // 지금 보여줄 흐름

  // 자식 모달의 effect가 다시 돌지 않도록 함수를 고정한다
  const toV1 = useCallback(() => setMode("V1"), []);
  const toV2 = useCallback(() => setMode("V2"), []);

  if (mode === "V2") {
    return (
      <PersonalizedModal
        userId={userId}
        onFallbackToV1={toV1}
        onClose={onClose}
      />
    );
  }
  return (
    <RecommendationModal
      userId={userId}
      onClose={onClose}
      onPersonalized={userId ? toV2 : undefined}
    />
  );
}
