import { useState, type CSSProperties } from "react";
import { friends, Icon, mySupplements, ProductVisual, TopBar, type Supplement } from "../shared/ui";

export function HomeScreen({
  completed,
  toggleComplete,
  onSurvey,
  onNotifications,
}: {
  completed: number[];
  toggleComplete: (id: number) => void;
  onSurvey: () => void;
  onNotifications: () => void;
}) {
  const todayItems = mySupplements;
  const percentage = Math.round((completed.length / todayItems.length) * 100);

  return (
    <div className="screen">
      <TopBar
        title="안녕하세요, 민지님"
        subtitle="10월 25일 금요일"
        onNotifications={onNotifications}
      />

      <section className="today-summary">
        <div className="today-copy">
          <span className="eyebrow">
            <Icon name="sparkles" size={15} />
            오늘의 건강 약속
          </span>
          <h2>
            오늘도 잊지 말고
            <br />
            챙겨 드세요
          </h2>
          <p>{todayItems.length - completed.length}개의 영양제가 남았어요</p>
        </div>
        <div
          className="summary-ring"
          style={{ "--progress": `${percentage * 3.6}deg` } as CSSProperties}
        >
          <div>
            <strong>{percentage}</strong>
            <span>%</span>
          </div>
        </div>
      </section>

      <section className="section-block">
        <div className="section-title-row">
          <div>
            <span className="section-label">TODAY</span>
            <h2>오늘 먹을 영양제</h2>
          </div>
          <span className="count-badge">{completed.length}/{todayItems.length} 완료</span>
        </div>

        <div className="today-list">
          {todayItems.map((item) => {
            const done = completed.includes(item.id);
            return (
              <button
                className={`today-item ${done ? "done" : ""}`}
                key={item.id}
                onClick={() => toggleComplete(item.id)}
              >
                <ProductVisual color={item.color} small />
                <span className="today-info">
                  <span>{item.time}</span>
                  <strong>{item.name}</strong>
                  <small>{item.ingredient}</small>
                </span>
                <span className="check-button">
                  {done && <Icon name="check" size={18} />}
                </span>
              </button>
            );
          })}
        </div>
      </section>

      <button className="recommend-banner" onClick={onSurvey}>
        <span className="recommend-icon">
          <Icon name="sparkles" size={23} />
        </span>
        <div>
          <span>맞춤 영양 추천</span>
          <h2>내 몸에 필요한 영양소가 궁금한가요?</h2>
          <p>간단한 건강 체크로 알아보세요</p>
        </div>
        <span className="recommend-arrow" aria-hidden="true">
          <Icon name="chevron" />
        </span>
      </button>
    </div>
  );
}

const surveyQuestions = [
  {
    title: "요즘 가장 신경 쓰이는 건강 고민은 무엇인가요?",
    description: "한 가지를 선택해주세요.",
    options: ["쉽게 피로해요", "잠을 푹 못 자요", "눈이 자주 피로해요", "장 건강이 걱정돼요"],
  },
  {
    title: "평소 식습관은 어떤 편인가요?",
    description: "가장 가까운 항목을 골라주세요.",
    options: ["규칙적으로 골고루 먹어요", "외식과 배달이 잦아요", "채소를 잘 안 먹어요", "식사를 자주 거르는 편이에요"],
  },
  {
    title: "일주일에 운동을 얼마나 하나요?",
    description: "가벼운 산책도 운동에 포함돼요.",
    options: ["거의 하지 않아요", "주 1~2회", "주 3~4회", "주 5회 이상"],
  },
];

const recommendationMap: Record<string, Supplement[]> = {
  "쉽게 피로해요": [
    {
      id: 101,
      name: "활성 비타민B 컴플렉스",
      brand: "쏜리서치",
      ingredient: "비타민B군 8종",
      amount: "60캡슐 · 1개월분",
      price: "38,900원",
      category: "에너지",
      color: "sun",
      time: "아침 식후 2캡슐",
    },
    mySupplements[2],
  ],
  "잠을 푹 못 자요": [mySupplements[2], mySupplements[1]],
  "눈이 자주 피로해요": [friends[0].supplements[2], mySupplements[1]],
  "장 건강이 걱정돼요": [friends[1].supplements[1], mySupplements[0]],
};

export function SurveyModal({ onClose }: { onClose: () => void }) {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<string[]>([]);
  const isResult = step === surveyQuestions.length;
  const recommendations = recommendationMap[answers[0]] ?? recommendationMap["쉽게 피로해요"];

  const choose = (answer: string) => {
    setAnswers((current) => [...current.slice(0, step), answer]);
    setStep((current) => current + 1);
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="survey-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="survey-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" aria-label="닫기" onClick={onClose}>×</button>
        {!isResult ? (
          <>
            <div className="survey-progress">
              <span style={{ width: `${((step + 1) / surveyQuestions.length) * 100}%` }} />
            </div>
            <span className="survey-step">STEP {step + 1} / {surveyQuestions.length}</span>
            <span className="survey-symbol"><Icon name="sparkles" size={25} /></span>
            <h2 id="survey-title">{surveyQuestions[step].title}</h2>
            <p>{surveyQuestions[step].description}</p>
            <div className="survey-options">
              {surveyQuestions[step].options.map((option) => (
                <button key={option} onClick={() => choose(option)}>
                  <span>{option}</span>
                  <Icon name="chevron" size={17} />
                </button>
              ))}
            </div>
            {step > 0 && (
              <button className="survey-back" onClick={() => setStep((current) => current - 1)}>
                이전 질문
              </button>
            )}
          </>
        ) : (
          <div className="survey-result">
            <span className="survey-symbol result"><Icon name="check" size={27} /></span>
            <span className="section-label">PERSONAL RESULT</span>
            <h2 id="survey-title">민지님께 필요한 영양소를 찾았어요</h2>
            <p>
              <strong>{answers[0]}</strong>라고 답해주신 내용을 중심으로 추천했어요.
            </p>
            <div className="result-list">
              {recommendations.map((item) => (
                <div className="result-item" key={item.id}>
                  <ProductVisual color={item.color} small />
                  <div>
                    <span>{item.category}</span>
                    <strong>{item.name}</strong>
                    <small>{item.ingredient}</small>
                  </div>
                  <b>{item.price}</b>
                </div>
              ))}
            </div>
            <div className="result-notice">
              본 결과는 임시 추천이며, 질환이나 복용 중인 약이 있다면 전문가와 상담해주세요.
            </div>
            <button className="result-button" onClick={onClose}>추천 결과 확인 완료</button>
          </div>
        )}
      </div>
    </div>
  );
}
