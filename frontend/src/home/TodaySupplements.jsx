// 오늘 먹을 영양제 목록 + 복용 체크 (카드 안에서 구분선으로 나눈 목록)
export default function TodaySupplements({ supplements, done, total, pendingIds = [], onToggle }) {
    return (
        <section className="home-today">
            {/* 제목 + 안내 */}
            <div className="home-card-head">
                <h3 className="home-card-title">오늘 먹을 영양제</h3>
                <span className="home-card-hint">
                    {total === 0 ? "" : `${done}/${total} 완료 · 눌러서 복용 체크`}
                </span>
            </div>

            {/* 영양제가 없을 때 / 있을 때 */}
            {total === 0 ? (
                <p className="home-empty">
                    오늘 복용할 영양제가 없어요. 마이페이지에서 영양제를 등록해보세요.
                </p>
            ) : (
                <ul className="home-supp-list">
                    {supplements.map((s) => (
                        <li key={s.id}>
                            <button
                                type="button"
                                className={`home-supp-row ${s.taken ? "is-taken" : ""}`}
                                onClick={() => onToggle(s.id)}
                                aria-pressed={s.taken}
                                disabled={pendingIds.includes(s.id)}
                            >
                                <span className="home-check" aria-hidden="true">
                                    {s.taken && (
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                                            <path d="m5 12 4 4L19 6" />
                                        </svg>
                                    )}
                                </span>

                                <span className="home-supp-info">
                                    <span className="home-supp-name">{s.name}</span>
                                    <span className="home-supp-desc">{s.desc}</span>
                                </span>

                                <span className="home-supp-timing">{s.timing}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
