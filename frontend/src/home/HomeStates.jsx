// 홈 상태별 화면 (#67 홈 공통 상태) — 영양제 없음 / 불러오는 중 / 체크 결과 토스트
// (모두 완료 상태는 PromiseCard가 "오늘의 약속을 모두 지켰어요"로 바뀌어서 보여준다)

/** 등록한 영양제가 없을 때 (첫 사용) */
export function EmptySupplements({ onAdd }) {
  return (
    <section className="home-empty-state">
      <span className="home-empty-art" aria-hidden="true">
        <span className="home-empty-cap" />
        <span className="home-empty-bottle" />
        <span className="home-empty-plus">+</span>
      </span>
      <strong>아직 등록한 영양제가 없어요</strong>
      <p>
        먹고 있는 영양제를 등록하면
        <br />
        시간에 맞춰 알려드려요
      </p>
      <button type="button" className="btn btn-primary" onClick={onAdd}>
        + 영양제 등록하기
      </button>
    </section>
  );
}

/** 홈 데이터를 불러오는 중 (스켈레톤) */
export function HomeSkeleton() {
  return (
    <div className="home-page" aria-busy="true" aria-label="홈 화면을 불러오는 중">
      <div className="home-inner">
        <div className="home-skel-head">
          <span className="home-skel" style={{ width: 120, height: 14 }} />
          <span className="home-skel" style={{ width: 280, height: 30 }} />
        </div>
        <div className="home-columns">
          <div className="home-main">
            <section className="home-today">
              <span className="home-skel" style={{ width: "36%", height: 16 }} />
              {[0, 1, 2].map((i) => (
                <div className="home-skel-row" key={i}>
                  <span className="home-skel home-skel-circle" />
                  <span className="home-skel-lines">
                    <span className="home-skel" style={{ width: "58%", height: 14 }} />
                    <span className="home-skel" style={{ width: "36%", height: 10 }} />
                  </span>
                  <span className="home-skel" style={{ width: 70, height: 10 }} />
                </div>
              ))}
            </section>
          </div>
          <aside className="home-side">
            <section className="home-promise is-loading">
              <span className="home-skel home-skel-ring" />
              <span className="home-skel-lines">
                <span className="home-skel" style={{ width: "80%", height: 14 }} />
                <span className="home-skel" style={{ width: "60%", height: 26 }} />
              </span>
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}

/** 체크 결과 토스트: 성공(되돌리기) / 실패(재시도) */
export function HomeToast({ toast, onAction, onClose }) {
  if (!toast) return null;
  const failed = toast.type === "error";
  return (
    <div className={`home-toast ${failed ? "is-error" : ""}`} role={failed ? "alert" : "status"}>
      <span className="home-toast-icon" aria-hidden="true">
        {failed ? "!" : (
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <path d="m5 12 4 4L19 6" />
          </svg>
        )}
      </span>
      <span className="home-toast-text">{toast.text}</span>
      {toast.actionLabel && (
        <button type="button" className="home-toast-action" onClick={onAction}>
          {toast.actionLabel}
        </button>
      )}
      <button type="button" className="home-toast-close" aria-label="닫기" onClick={onClose}>
        ×
      </button>
    </div>
  );
}
