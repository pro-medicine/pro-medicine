// 민감정보 동의 단계 (담당: 영준) — 회원이 아직 동의하지 않았을 때만 보인다

/**
 * props (RecommendationModal에서 전달)
 * - sending: 동의 요청 중 여부
 * - onAgree: 동의하기
 * - onSkip: 민감 문항 없이 진행
 */
export default function ConsentStep({ sending, onAgree, onSkip }) {
  return (
    <div className="rec-consent">
      <h3>더 정확한 추천을 위해 건강 정보가 필요해요</h3>
      <p>
        임신·수유 여부, 앓고 있는 질환, 복용 중인 약을 함께 알려주시면 함께
        먹으면 안 되는 성분을 걸러서 추천해 드려요.
      </p>
      <ul className="rec-consent-list">
        <li>수집 항목: 임신·수유 여부, 질환, 복용 중인 약</li>
        <li>이용 목적: 영양 성분 추천과 안전 확인</li>
        <li>동의하지 않아도 나머지 문항으로 추천받을 수 있어요</li>
      </ul>
      <div className="rec-actions">
        <button
          type="button"
          className="rec-button primary"
          disabled={sending}
          onClick={onAgree}
        >
          {sending ? "처리 중..." : "동의하고 계속"}
        </button>
        <button
          type="button"
          className="rec-button"
          disabled={sending}
          onClick={onSkip}
        >
          동의 없이 진행
        </button>
      </div>
    </div>
  );
}
