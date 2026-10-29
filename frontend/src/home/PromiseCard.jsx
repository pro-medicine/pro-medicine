// 오늘의 약속 카드: 오늘 챙긴 개수 / 전체 개수 + 진행 막대
// (props는 그대로: percent, remain, total → 챙긴 개수 = total - remain)

// 남은 개수에 따라 아래 문구 변경
function subText(remain, total) {
    if (total === 0) return "등록된 영양제가 없어요";
    if (remain === 0) return "오늘의 약속을 모두 지켰어요!";
    return `${remain}개만 더 챙기면 오늘 완료예요`;
}

export default function PromiseCard({ percent, remain, total }) {
    const done = total - remain;

    return (
        <section className="home-promise">
            <p className="home-card-label">오늘의 약속</p>
            <p className="home-promise-count">
                <strong>{done}</strong>
                <span>/ {total}</span>
            </p>
            <div className="home-promise-bar" role="img" aria-label={`오늘 복용률 ${percent}%`}>
                <span style={{ width: `${percent}%` }} />
            </div>
            <p className="home-promise-sub">{subText(remain, total)}</p>
        </section>
    );
}
