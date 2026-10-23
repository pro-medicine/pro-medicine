// 오늘의 건강 약속 카드 + 원형 복용률 그래프
const R = 54; // 원의 반지름
const CIRCUMFERENCE = 2 * Math.PI * R; // 원 둘레 (약 339)

// 남은 개수에 따라 아래 문구 변경
function subText(remain, total) {
    if (total === 0) return "등록된 영양제가 없어요";
    if (remain === 0) return "오늘의 약속을 모두 지켰어요!";
    return `${remain}개의 영양제가 남았어요`;
}

export default function PromiseCard({ percent, remain, total }) {
    // 안 채워진 길이 = 둘레 × (남은 비율)
    const offset = CIRCUMFERENCE * (1 - percent / 100);

    return (
        <section className="home-promise">
            <div>
                <p className="home-promise-label">✦ 오늘의 건강 약속</p>
                <h2 className="home-promise-title">
                    오늘도 잊지 말고
                    <br />
                    챙겨 드세요
                </h2>
                <p className="home-promise-sub">{subText(remain, total)}</p>
            </div>

            <div className="home-ring" role="img" aria-label={`오늘 복용률 ${percent}%`}>
                <svg width="140" height="140" viewBox="0 0 140 140">
                    <circle
                        cx="70"
                        cy="70"
                        r={R}
                        className="home-ring-fg"
                        strokeDasharray={CIRCUMFERENCE}
                        strokeDashoffset={offset}
                        transform="rotate(-90 70 70)"
                    />
                </svg>
                <div className="home-ring-text">
                    <strong>{percent}</strong>
                    <span>%</span>
                </div>
            </div>
        </section>
    );
}