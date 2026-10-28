import {
  Icon,
  mySupplements,
  SupplementCard,
  TopBar,
  type Friend,
} from "../shared/ui";

export default function ProfileScreen({
  friend,
  onBack,
  onNotifications,
  topBarActions,
}: {
  friend?: Friend;
  onBack?: () => void;
  onNotifications: () => void;
}) {
  const isMine = !friend;
  const supplements = friend?.supplements ?? mySupplements;
  const name = friend?.name ?? "김민지";
  const initial = friend?.initial ?? "민";
  const avatarColor = friend?.color ?? "yellow";
  const streak = friend?.streak ?? 12;

  return (
    <div className="screen profile-screen">
      <TopBar
        back={onBack}
        title={isMine ? "마이페이지" : `${name}님의 영양정보`}
        subtitle={isMine ? "나의 건강 기록" : "친구 프로필"}
        onNotifications={onNotifications}
        actions={topBarActions}
      />

      <section className="profile-hero">
        <span className={`avatar xlarge ${avatarColor}`}>{initial}</span>
        <div className="profile-name">
          <span>{isMine ? "건강한 하루를 만드는 중" : friend?.relation}</span>
          <h2>{name}</h2>
          <p>{isMine ? "꾸준함이 최고의 영양제예요" : friend?.status}</p>
        </div>
        {isMine && <button className="edit-button">프로필 편집</button>}
      </section>

      <section className="stats-grid">
        <div>
          <span>연속 복용</span>
          <strong>
            {streak}
            <small>일</small>
          </strong>
        </div>
        <div>
          <span>복용 영양제</span>
          <strong>
            {supplements.length}
            <small>개</small>
          </strong>
        </div>
        <div>
          <span>이번 달 달성률</span>
          <strong>
            {isMine ? "87" : "92"}
            <small>%</small>
          </strong>
        </div>
      </section>

      {isMine && <NutritionManagement />}

      <section className="section-block products-section">
        <div className="section-title-row">
          <div>
            <span className="section-label">SUPPLEMENTS</span>
            <h2>{isMine ? "내가 먹는 영양제" : `${name}님이 먹는 영양제`}</h2>
          </div>
          {isMine && (
            <button className="add-friend-button">
              <Icon name="plus" size={16} />
              영양제 등록
            </button>
          )}
        </div>
        <p className="section-description">
          상품명과 주요 영양성분, 함량 및 구매 가격을 확인할 수 있어요.
        </p>
        <div className="product-grid">
          {supplements.map((supplement) => (
            <SupplementCard key={supplement.id} supplement={supplement} />
          ))}
        </div>
      </section>
    </div>
  );
}

const nutritionData = [
  {
    name: "비타민 C",
    intake: "100mg",
    target: "100mg",
    percent: 100,
    tone: "yellow",
  },
  {
    name: "비타민 D",
    intake: "20㎍",
    target: "10㎍",
    percent: 100,
    original: 200,
    tone: "green",
  },
  {
    name: "마그네슘",
    intake: "280mg",
    target: "350mg",
    percent: 80,
    tone: "purple",
  },
  {
    name: "칼슘",
    intake: "350mg",
    target: "700mg",
    percent: 50,
    tone: "orange",
  },
  {
    name: "오메가3",
    intake: "600mg",
    target: "500mg",
    percent: 100,
    original: 120,
    tone: "blue",
  },
];

function NutritionManagement() {
  return (
    <section className="nutrition-management">
      <div className="nutrition-heading">
        <div>
          <span className="section-label">DAILY NUTRITION</span>
          <h2>나의 영양관리</h2>
          <p>복용 중인 영양제를 기준으로 성인 하루 권장량과 비교했어요.</p>
        </div>
        <span className="nutrition-date">오늘 기준</span>
      </div>
      <div className="nutrition-list">
        {nutritionData.map((item) => (
          <div className="nutrition-row" key={item.name}>
            <div className="nutrition-name">
              <strong>{item.name}</strong>
              <span>
                {item.intake} / 권장 {item.target}
              </span>
            </div>
            <div className="nutrition-gauge">
              <span
                className={item.tone}
                style={{ width: `${Math.min(item.percent, 100)}%` }}
              />
            </div>
            <div className="nutrition-percent">
              <strong>{item.original ?? item.percent}%</strong>
              {(item.original ?? item.percent) > 100 && <span>충분</span>}
            </div>
          </div>
        ))}
      </div>
      <div className="nutrition-notice">
        <Icon name="sparkles" size={17} />
        <p>
          칼슘이 권장량보다 부족해요. 식품 섭취량은 포함되지 않은 참고용
          수치예요.
        </p>
      </div>
    </section>
  );
}
