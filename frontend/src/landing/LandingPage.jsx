// 서비스 소개(첫 화면) — 처음 접속하면 보이는 화면 (#67 시안 B)
// 지금은 로그인이 없어서 "시작하기"를 누르면 바로 홈으로 들어간다.
// TODO: 로그인(#52) 연동 후 "로그인" / "시작하기"를 로그인·회원가입 화면으로 연결
import { useState } from "react";
import { Icon } from "../shared/ui";
import "./landing.css";

const FEATURES = [
  { icon: "clock", title: "복용 알림과 체크", desc: "정해둔 시간에 알림을 받고, 한 번에 체크해요." },
  { icon: "sparkles", title: "맞춤 추천", desc: "생활 습관 설문으로 필요한 성분을 알려줘요." },
  { icon: "people", title: "친구와 함께", desc: "친구의 복용 달력을 보며 서로 응원해요." },
  { icon: "pin", title: "폐의약품 지도", desc: "가까운 수거함과 버리는 방법을 알려줘요." },
];

const STEPS = [
  { title: "영양제 등록", desc: "먹고 있는 영양제와 복용 시간을 등록해요." },
  { title: "알림 받고 체크", desc: "시간이 되면 알려드리고, 먹은 영양제를 체크해요." },
  { title: "친구와 함께", desc: "친구를 추가하고 서로의 복용 달력을 확인해요." },
];

// 소개용 예시 (실제 사용자 데이터 아님) — 눌러서 체크해 볼 수 있다
const DEMO = [
  { id: 1, name: "종합비타민", slot: "아침" },
  { id: 2, name: "오메가3", slot: "점심" },
  { id: 3, name: "마그네슘", slot: "저녁" },
];

function todayLabel() {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
  }).format(new Date());
}

/**
 * props (App.jsx에서 전달)
 * - onStart(tab?): 서비스 시작 (tab을 주면 그 화면으로, 없으면 홈)
 */
export default function LandingPage({ onStart }) {
  const [checked, setChecked] = useState([1]);
  const done = checked.length;

  const toggle = (id) =>
    setChecked((prev) => (prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]));

  const scrollTo = (id) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="landing">
      {/* 상단 메뉴 */}
      <header className="landing-nav">
        <div className="landing-nav-inner">
          <button type="button" className="top-nav-logo" onClick={() => scrollTo("landing-top")}>
            <span className="top-nav-mark" aria-hidden="true" />
            약쏙해줘
          </button>
          <nav className="landing-menu" aria-label="소개 메뉴">
            <button type="button" className="active" onClick={() => scrollTo("landing-top")}>소개</button>
            <button type="button" onClick={() => scrollTo("landing-features")}>기능</button>
            <button type="button" onClick={() => scrollTo("landing-steps")}>이용 방법</button>
            <button type="button" onClick={() => onStart("disposal")}>폐의약품 지도</button>
          </nav>
          <div className="landing-nav-actions">
            {/* TODO: 로그인(#52) 연동 후 로그인 화면으로 */}
            <button type="button" className="landing-login" onClick={() => onStart()}>로그인</button>
            <button type="button" className="btn btn-dark btn-sm" onClick={() => onStart()}>시작하기</button>
          </div>
        </div>
      </header>

      <main>
        {/* 첫 화면 */}
        <section className="landing-hero" id="landing-top">
          <p className="page-eyebrow">영양제 복용 관리</p>
          <h1>
            영양제, 이제
            <br />
            약속처럼 챙겨요
          </h1>
          <p className="landing-hero-sub">알림을 받고, 체크하고, 친구와 함께 꾸준히.</p>
          <div className="landing-hero-actions">
            <button type="button" className="btn btn-primary btn-lg" onClick={() => onStart()}>
              무료로 시작하기
            </button>
            <button type="button" className="landing-more" onClick={() => scrollTo("landing-features")}>
              기능 보기 →
            </button>
          </div>

          {/* 미리보기 카드 (예시) */}
          <div className="landing-preview" aria-label="오늘 먹을 영양제 예시">
            <div className="landing-preview-list">
              <div className="landing-preview-head">
                <strong>오늘 먹을 영양제</strong>
                <span>{todayLabel()}</span>
              </div>
              <ul>
                {DEMO.map((item) => {
                  const on = checked.includes(item.id);
                  return (
                    <li key={item.id}>
                      <button
                        type="button"
                        className={on ? "is-on" : ""}
                        aria-pressed={on}
                        onClick={() => toggle(item.id)}
                      >
                        <span className="landing-check" aria-hidden="true">
                          {on && <Icon name="check" size={13} />}
                        </span>
                        <span className="landing-preview-name">{item.name}</span>
                        <span className="landing-preview-slot">{item.slot}</span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
            <div className="landing-preview-promise">
              <span>오늘의 약속</span>
              <p>
                <strong>{done}</strong>/{DEMO.length}
              </p>
              <div className="landing-bar">
                <span style={{ width: `${(done / DEMO.length) * 100}%` }} />
              </div>
            </div>
          </div>
        </section>

        {/* 기능 */}
        <section className="landing-section" id="landing-features">
          <p className="page-eyebrow">기능</p>
          <h2>필요한 것만, 간단하게</h2>
          <div className="landing-features">
            {FEATURES.map((f) => (
              <div key={f.title}>
                <span className="landing-feature-icon">
                  <Icon name={f.icon} size={22} />
                </span>
                <strong>{f.title}</strong>
                <p>{f.desc}</p>
              </div>
            ))}
          </div>
        </section>

        {/* 이용 방법 */}
        <section className="landing-steps-band" id="landing-steps">
          <div className="landing-steps-inner">
            <div>
              <p className="page-eyebrow">이용 방법</p>
              <h2>
                세 단계면
                <br />
                충분해요
              </h2>
            </div>
            <ol className="landing-steps">
              {STEPS.map((s, i) => (
                <li key={s.title}>
                  <span>{String(i + 1).padStart(2, "0")}</span>
                  <div>
                    <strong>{s.title}</strong>
                    <p>{s.desc}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* 마지막 시작 버튼 */}
        <section className="landing-cta">
          <h2>오늘부터 약속처럼 챙겨볼까요?</h2>
          <button type="button" className="btn btn-primary btn-lg" onClick={() => onStart()}>
            무료로 시작하기
          </button>
        </section>
      </main>

      <footer className="landing-footer">
        <span>© 약쏙해줘</span>
        <span>건강기능식품 정보는 진단이나 처방을 대신하지 않아요.</span>
      </footer>
    </div>
  );
}
