// 로그인 · 회원가입 화면 (#52 로그인 기능 + #67 새 디자인)
// - 넓은 화면: 왼쪽 서비스 소개 패널 + 오른쪽 입력 카드 / 좁은 화면: 입력 카드만
// - 로그인·회원가입 요청과 입력 검사는 #52와 같음 (authApi.js)
import { useState } from "react";
import { login, signup } from "./authApi";
import "./auth.css";

const GENDERS = ["여성", "남성", "선택 안 함"];

function EyeIcon({ open }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {!open && <path d="M3 3l18 18" />}
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 12.5 10 17 19 7" />
    </svg>
  );
}

// 비밀번호 입력 + 보기/숨기기 버튼
function PasswordInput({ id, value, onChange, show, onToggle, ...rest }) {
  return (
    <span className="auth-input-wrap">
      <input id={id} type={show ? "text" : "password"} value={value} onChange={onChange} {...rest} />
      <button
        type="button"
        className="auth-eye"
        aria-label={show ? "비밀번호 숨기기" : "비밀번호 보기"}
        aria-pressed={show}
        onClick={onToggle}
      >
        <EyeIcon open={show} />
      </button>
    </span>
  );
}

// 왼쪽 소개 패널: 오늘 복용 미리보기 카드
function BrandPanel({ isSignup }) {
  return (
    <section className="auth-brand-panel" aria-hidden="true">
      <p className="auth-eyebrow">{isSignup ? "JOIN YAKSSOK" : "WELCOME BACK"}</p>
      <h2>
        {isSignup ? (
          <>
            건강한 약속,
            <br />
            오늘부터 시작해요
          </>
        ) : (
          <>
            오늘의 약속,
            <br />
            잊지 않게 챙겨드릴게요
          </>
        )}
      </h2>
      <p className="auth-brand-sub">알림을 받고, 체크하고, 친구와 함께 꾸준히.</p>

      <div className="auth-preview">
        <div className="auth-preview-head">
          <strong>오늘 복용</strong>
          <span className="auth-preview-streak">🔥 12일 연속</span>
        </div>
        <ul>
          <li className="is-done">
            <span className="auth-preview-check"><CheckIcon /></span>
            <span>
              <b>비타민 D</b>
              <small>아침 식후 · 08:30</small>
            </span>
          </li>
          <li className="is-done">
            <span className="auth-preview-check"><CheckIcon /></span>
            <span>
              <b>오메가3</b>
              <small>점심 식후 · 12:30</small>
            </span>
          </li>
          <li>
            <span className="auth-preview-check" />
            <span>
              <b>마그네슘</b>
              <small>저녁 식후 · 19:00</small>
            </span>
            <em>곧 알림</em>
          </li>
        </ul>
        <div className="auth-preview-bar">
          <span style={{ width: "67%" }} />
        </div>
        <p className="auth-preview-foot">2 / 3 완료 · 오늘 약속까지 1개 남았어요</p>
      </div>
    </section>
  );
}

export default function AuthPage({ mode, notice = "", onModeChange, onComplete, onClose }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [age, setAge] = useState("");
  const [gender, setGender] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const isSignup = mode === "signup";

  // 로그인 / 회원가입 모드 전환 시 오류 메시지 초기화
  const changeMode = (nextMode) => {
    if (busy) return;
    setError("");
    setShowPassword(false);
    onModeChange(nextMode);
  };

  // 입력 중 안내 (회원가입)
  const passwordOk = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(password);
  const confirmMismatch = isSignup && confirmPassword.length > 0 && password !== confirmPassword;

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    if (isSignup && password !== confirmPassword) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    if (isSignup && !passwordOk) {
      setError("비밀번호는 영문과 숫자를 포함해 8~72자로 입력해주세요.");
      return;
    }
    if (isSignup && (!Number.isInteger(Number(age)) || Number(age) < 1 || Number(age) > 120)) {
      setError("만 나이는 1~120 사이의 정수로 입력해주세요.");
      return;
    }
    if (isSignup && !gender) {
      setError("성별을 선택해주세요.");
      return;
    }
    setError("");
    setBusy(true);
    try {
      const user = isSignup
        ? await signup({
            name: name.trim(),
            email: email.trim(),
            password,
            age: Number(age),
            gender,
            termsAgreed: agreed,
          })
        : await login({ email: email.trim(), password, rememberMe });
      onComplete(user);
    } catch (cause) {
      setError(cause.message || "요청을 완료하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`auth-page${isSignup ? " is-signup" : ""}`}>
      <header className="auth-header">
        <div className="auth-header-inner">
          <button
            type="button"
            className="top-nav-logo"
            onClick={() => {
              setError("");
              onClose();
            }}
            aria-label="약쏙해줘 소개 화면으로"
          >
            <span className="top-nav-mark" aria-hidden="true" />
            약쏙해줘
          </button>
          <p className="auth-header-switch">
            <span>{isSignup ? "이미 회원이신가요?" : "처음이신가요?"}</span>
            <button type="button" disabled={busy} onClick={() => changeMode(isSignup ? "login" : "signup")}>
              {isSignup ? "로그인" : "회원가입"}
            </button>
          </p>
        </div>
      </header>

      <main className="auth-content">
        <BrandPanel isSignup={isSignup} />

        <section className="auth-card" aria-labelledby="auth-title">
          <div className="auth-tabs" role="tablist" aria-label="로그인 또는 회원가입">
            <button
              type="button"
              role="tab"
              aria-selected={!isSignup}
              disabled={busy}
              className={!isSignup ? "active" : ""}
              onClick={() => changeMode("login")}
            >
              로그인
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={isSignup}
              disabled={busy}
              className={isSignup ? "active" : ""}
              onClick={() => changeMode("signup")}
            >
              회원가입
            </button>
          </div>

          <div className="auth-card-head">
            <h1 id="auth-title">{isSignup ? "약쏙해줘 시작하기" : "다시 만나서 반가워요"}</h1>
            <p>
              {isSignup
                ? "가입하고 나에게 맞는 영양제와 복용 습관을 한 곳에서 관리하세요."
                : "로그인하고 오늘의 영양제 복용 일정을 확인하세요."}
            </p>
          </div>

          {notice && (
            <p className="auth-notice" role="status">
              {notice}
            </p>
          )}

          <form onSubmit={submit}>
            {isSignup && (
              <label className="auth-field">
                <span>이름</span>
                <input
                  required
                  maxLength={50}
                  autoComplete="nickname"
                  placeholder="이름을 입력해주세요"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
            )}

            <label className="auth-field">
              <span>이메일</span>
              <input
                required
                type="email"
                maxLength={255}
                autoComplete="email"
                placeholder="example@email.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <div className="auth-field">
              <label htmlFor="auth-password">비밀번호</label>
              <PasswordInput
                id="auth-password"
                required
                minLength={isSignup ? 8 : undefined}
                maxLength={isSignup ? 72 : undefined}
                autoComplete={isSignup ? "new-password" : "current-password"}
                placeholder={isSignup ? "영문, 숫자 포함 8자 이상" : "비밀번호를 입력해주세요"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                show={showPassword}
                onToggle={() => setShowPassword((v) => !v)}
              />
              {isSignup && password.length > 0 && (
                <small className={`auth-hint${passwordOk ? " is-ok" : ""}`}>
                  {passwordOk ? "✓ 사용할 수 있는 비밀번호예요" : "영문과 숫자를 포함해 8자 이상 입력해주세요"}
                </small>
              )}
            </div>

            {isSignup && (
              <>
                <div className="auth-field">
                  <label htmlFor="auth-password-confirm">비밀번호 확인</label>
                  <PasswordInput
                    id="auth-password-confirm"
                    required
                    minLength={8}
                    autoComplete="new-password"
                    placeholder="비밀번호를 한 번 더 입력해주세요"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    show={showPassword}
                    onToggle={() => setShowPassword((v) => !v)}
                    aria-invalid={confirmMismatch || undefined}
                  />
                  {confirmMismatch && <small className="auth-hint is-error">비밀번호가 일치하지 않아요</small>}
                </div>

                <div className="auth-profile-fields">
                  <label className="auth-field">
                    <span>만 나이</span>
                    <input
                      required
                      type="number"
                      min="1"
                      max="120"
                      step="1"
                      inputMode="numeric"
                      placeholder="예: 25"
                      value={age}
                      onChange={(event) => setAge(event.target.value)}
                    />
                  </label>
                  <div className="auth-field">
                    <span id="auth-gender-label">성별</span>
                    <div className="auth-segment" role="radiogroup" aria-labelledby="auth-gender-label">
                      {GENDERS.map((option) => (
                        <button
                          key={option}
                          type="button"
                          role="radio"
                          aria-checked={gender === option}
                          className={gender === option ? "active" : ""}
                          onClick={() => setGender(option)}
                        >
                          {option}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <label className="auth-check">
                  <input
                    type="checkbox"
                    required
                    checked={agreed}
                    onChange={(event) => setAgreed(event.target.checked)}
                  />
                  <span className="auth-check-box" aria-hidden="true">
                    <CheckIcon />
                  </span>
                  <span>서비스 이용약관 및 개인정보 수집에 동의합니다.</span>
                </label>
              </>
            )}

            {!isSignup && (
              <div className="auth-options">
                <label className="auth-check">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(event) => setRememberMe(event.target.checked)}
                  />
                  <span className="auth-check-box" aria-hidden="true">
                    <CheckIcon />
                  </span>
                  <span>로그인 유지</span>
                </label>
                <button type="button" onClick={() => setError("비밀번호 재설정 기능은 아직 준비 중입니다.")}>
                  비밀번호 찾기
                </button>
              </div>
            )}

            {error && (
              <p className="auth-error" role="alert">
                {error}
              </p>
            )}

            <button
              className="btn btn-dark btn-lg auth-submit"
              disabled={busy || (isSignup && (!agreed || !age || !gender))}
              type="submit"
            >
              {busy ? "처리 중..." : isSignup ? "회원가입하고 시작하기" : "로그인"}
            </button>
          </form>

          <p className="auth-switch">
            {isSignup ? "이미 회원이신가요?" : "아직 회원이 아니신가요?"}
            <button type="button" disabled={busy} onClick={() => changeMode(isSignup ? "login" : "signup")}>
              {isSignup ? "로그인" : "회원가입"}
            </button>
          </p>
        </section>
      </main>
    </div>
  );
}
