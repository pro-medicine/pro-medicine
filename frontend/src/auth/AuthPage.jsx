import { useState } from "react";
import { Icon } from "../shared/ui";
import { login, signup } from "./authApi";
import "./auth.css";

export default function AuthPage({ mode, onModeChange, onComplete, onClose }) {
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [age, setAge] = useState("");
    const [gender, setGender] = useState("");
    const [agreed, setAgreed] = useState(false);
    const [rememberMe, setRememberMe] = useState(false);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const isSignup = mode === "signup";

    // 로그인 / 회원가입 모드 전환 시 오류 메시지 초기화
    const changeMode = (nextMode) => {
        setError("");
        onModeChange(nextMode);
    };

    const submit = async (event) => {
        event.preventDefault();
        if (busy) return;
        if (isSignup && password !== confirmPassword) {
            setError("비밀번호 확인이 일치하지 않습니다.");
            return;
        }
        if (isSignup && !/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(password)) {
            setError("비밀번호는 영문과 숫자를 포함해 8~72자로 입력해주세요.");
            return;
        }
        if (isSignup && (!Number.isInteger(Number(age)) || Number(age) < 1 || Number(age) > 120)) {
            setError("만 나이는 1~120 사이의 정수로 입력해주세요.");
            return;
        }
        setError("");
        setBusy(true);
        try {
            const user = isSignup
                ? await signup({
                    name: name.trim(), email: email.trim(), password,
                    age: Number(age), gender, termsAgreed: agreed,
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
        <div className="auth-page">
            <header className="auth-header">
                <button className="auth-brand" onClick={onClose} type="button">
                    <span><Icon name="sparkles" size={20} /></span>
                    약쏙해줘
                </button>
                <div className="auth-header-tabs">
                    <button type="button" disabled={busy} className={!isSignup ? "active" : ""} onClick={() => changeMode("login")}>로그인</button>
                    <button type="button" disabled={busy} className={isSignup ? "active" : ""} onClick={() => changeMode("signup")}>회원가입</button>
                </div>
            </header>

            <main className="auth-content">
                <section className="auth-welcome">
                    <span className="auth-symbol"><Icon name={isSignup ? "sparkles" : "user"} size={26} /></span>
                    <span className="section-label">{isSignup ? "JOIN YAKSSOK" : "WELCOME BACK"}</span>
                    <h1>{isSignup ? "건강한 약속을 시작해볼까요?" : "다시 만나서 반가워요"}</h1>
                    <p>{isSignup
                        ? "가입하고 나에게 맞는 영양제와 복용 습관을 한 곳에서 관리하세요."
                        : "로그인하고 오늘의 영양제 복용 일정을 확인하세요."}</p>
                </section>

                <section className="auth-card">
                    <div className="auth-card-tabs">
                        <button type="button" disabled={busy} className={!isSignup ? "active" : ""} onClick={() => changeMode("login")}>로그인</button>
                        <button type="button" disabled={busy} className={isSignup ? "active" : ""} onClick={() => changeMode("signup")}>회원가입</button>
                    </div>
                    <form onSubmit={submit}>
                        {isSignup && (
                            <label className="auth-field">
                                <span>이름</span>
                                <input required maxLength={50} autoComplete="nickname" onChange={(event) => setName(event.target.value)} placeholder="이름을 입력해주세요" value={name} />
                            </label>
                        )}
                        <label className="auth-field">
                            <span>이메일</span>
                            <input required type="email" maxLength={255} autoComplete="email" placeholder="example@email.com" value={email} onChange={(event) => setEmail(event.target.value)} />
                        </label>
                        <label className="auth-field">
                            <span>비밀번호</span>
                            <input required minLength={isSignup ? 8 : undefined} maxLength={isSignup ? 72 : undefined} type="password" autoComplete={isSignup ? "new-password" : "current-password"} placeholder={isSignup ? "영문, 숫자 포함 8자 이상" : "비밀번호를 입력해주세요"} value={password} onChange={(event) => setPassword(event.target.value)} />
                        </label>
                        {isSignup && (
                            <>
                                <label className="auth-field">
                                    <span>비밀번호 확인</span>
                                    <input required minLength={8} type="password" autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해주세요" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
                                </label>
                                <div className="auth-profile-fields">
                                    <label className="auth-field">
                                        <span>만 나이</span>
                                        <input required type="number" min="1" max="120" step="1" inputMode="numeric"
                                            placeholder="예: 25" value={age}
                                            onChange={(event) => setAge(event.target.value)} />
                                    </label>
                                    <label className="auth-field">
                                        <span>성별</span>
                                        <select required value={gender} onChange={(event) => setGender(event.target.value)}>
                                            <option value="" disabled>선택</option>
                                            <option>여성</option><option>남성</option><option>선택 안 함</option>
                                        </select>
                                    </label>
                                </div>
                                <label className="agreement-row">
                                    <input checked={agreed} onChange={(event) => setAgreed(event.target.checked)} required type="checkbox" />
                                    <span>서비스 이용약관 및 개인정보 수집에 동의합니다.</span>
                                </label>
                            </>
                        )}
                        {!isSignup && (
                            <div className="login-options">
                                <label><input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} /> 로그인 유지</label>
                                <button type="button" onClick={() => setError("비밀번호 재설정 기능은 아직 준비 중입니다.")}>비밀번호 찾기</button>
                            </div>
                        )}
                        {error && <p className="auth-error" role="alert">{error}</p>}
                        <button className="auth-submit" disabled={busy || (isSignup && (!agreed || !age || !gender))} type="submit">
                            {busy ? "처리 중..." : isSignup ? "회원가입하고 시작하기" : "로그인"}
                        </button>
                    </form>
                    <p className="auth-switch">
                        {isSignup ? "이미 회원이신가요?" : "아직 회원이 아니신가요?"}
                        <button type="button" onClick={() => changeMode(isSignup ? "login" : "signup")}>{isSignup ? "로그인" : "회원가입"}</button>
                    </p>
                </section>
            </main>
        </div>
    );
}
