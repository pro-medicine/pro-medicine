// 영양제 병 일러스트 (색상만 바꿔서 재사용)
// 사용 예: <SupplementBottle color="#d6b44c" />
export default function SupplementBottle({ color, size = 40 }) {
    return (
        <div
            className="home-bottle"
            style={{ "--home-bottle": color, width: size, height: size * 1.6 }}
        >
            <div className="home-bottle-cap" />
            <div className="home-bottle-body">
                <div className="home-bottle-label">
                    <span className="home-bottle-label-sub">NUTRITION</span>
                    <span className="home-bottle-label-main">DAILY</span>
                </div>
            </div>
        </div>
    );
}