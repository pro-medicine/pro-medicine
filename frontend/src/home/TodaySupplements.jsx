// 오늘 먹을 영양제 목록 + 복용 체크
import SupplementBottle from "./SupplementBottle";

export default function TodaySupplements({ supplements, done, total, onToggle }) {
    return (
        <section className="home-today">
            {/* 제목 + 완료 개수 */}
            <div className="home-section-head">
                <div>
                    <p className="home-eyebrow">TODAY</p>
                    <h3 className="home-section-title">오늘 먹을 영양제</h3>
                </div>
                <span className="home-pill">
          {done}/{total} 완료
        </span>
            </div>

            {/* 영양제가 없을 때 / 있을 때 */}
            {total === 0 ? (
                <p className="home-empty">
                    오늘 복용할 영양제가 없어요. 마이페이지에서 영양제를 등록해보세요.
                </p>
            ) : (
                <div className="home-supp-grid">
                    {supplements.map((s) => (
                        <button
                            key={s.id}
                            type="button"
                            className={`home-supp-card ${s.taken ? "is-taken" : ""}`}
                            onClick={() => onToggle(s.id)}
                            aria-pressed={s.taken}
                        >
                            <SupplementBottle color={s.color} />

                            <div className="home-supp-info">
                                <p className="home-supp-timing">{s.timing}</p>
                                <p className="home-supp-name">{s.name}</p>
                                <p className="home-supp-desc">{s.desc}</p>
                            </div>

                            <span className="home-check">{s.taken && "✓"}</span>
                        </button>
                    ))}
                </div>
            )}
        </section>
    );
}