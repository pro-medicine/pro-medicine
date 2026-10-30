// 오늘의 약속 카드: 원형 진행률 + 남은 개수 문구 + 연속 복용 배지 (#67 홈 시안 A)
// (props: percent, remain, total, streakDays — 챙긴 개수 = total - remain)
const SIZE = 112;
const STROKE = 10;
const R = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * R;

// 남은 개수에 따라 문구 변경
function subText(remain, total) {
    if (total === 0) return "등록된 영양제가 없어요";
    if (remain === 0) return "오늘의 약속을 모두 지켰어요!";
    return `${remain}개만 더 챙기면 오늘 완료예요`;
}

export default function PromiseCard({ percent, remain, total, streakDays }) {
    const done = total - remain;
    const offset = CIRCUMFERENCE * (1 - percent / 100);

    return (
        <section className="home-promise">
            <div className="home-ring" role="img" aria-label={`오늘의 약속 ${done}/${total}, ${percent}%`}>
                <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} aria-hidden="true">
                    <circle cx={SIZE / 2} cy={SIZE / 2} r={R} className="home-ring-track" />
                    <circle
                        cx={SIZE / 2}
                        cy={SIZE / 2}
                        r={R}
                        className="home-ring-fg"
                        strokeDasharray={CIRCUMFERENCE}
                        strokeDashoffset={offset}
                        transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
                    />
                </svg>
                <span className="home-ring-text">
                    <strong>{done}/{total}</strong>
                    <small>오늘의 약속</small>
                </span>
            </div>
            <div className="home-promise-copy">
                <p className="home-promise-sub">{subText(remain, total)}</p>
                {streakDays > 0 && <StreakBadge days={streakDays} />}
            </div>
        </section>
    );
}

export function StreakBadge({ days }) {
    return (
        <span className="home-streak">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 3c1 3.5 5 5.5 5 10a5 5 0 0 1-10 0c0-2 1-3.5 2-4.5 0 2 1 3 2 3 0-3-1-5.5 1-8.5Z" />
            </svg>
            {days}일 연속 복용 중
        </span>
    );
}
