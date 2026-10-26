
import { useEffect, useState } from "react";
import HomePage from "./home";
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout } from "./auth/authApi";

export default function App() {
    const [user, setUser] = useState(null);
    const [authMode, setAuthMode] = useState("login");
    const [loading, setLoading] = useState(true);
    const [loggingOut, setLoggingOut] = useState(false);
    const [error, setError] = useState("");

    // 최초 접속 시 로그인 상태 확인
    useEffect(() => {
        let active = true;

        getCurrentUser()
            .then((currentUser) => {
                if (active) {
                    setUser(currentUser);
                }
            })
            .catch(() => {
                if (active) {
                    setError("서버 연결 또는 로그인 상태 확인에 실패했습니다.");
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, []);

    // 로그인 또는 회원가입 성공
    const handleAuthComplete = (account) => {
        setUser(account);
        setError("");
    };

    // 로그아웃 처리
    const handleLogout = async () => {
        if (loggingOut) return;

        setLoggingOut(true);
        setError("");

        try {
            await logout();
            setUser(null);
            setAuthMode("login");
        } catch (err) {
            setError(err.message || "로그아웃에 실패했습니다.");
        } finally {
            setLoggingOut(false);
        }
    };

    // 로그인 상태 확인 중
    if (loading) {
        return (
            <div className="auth-page" role="status">
                로그인 상태를 확인하는 중...
            </div>
        );
    }

    // 비로그인 상태: 로그인/회원가입 화면
    if (!user) {
        return (
            <>
                {error && <p role="alert">{error}</p>}

                <AuthPage
                    mode={authMode}
                    onModeChange={setAuthMode}
                    onComplete={handleAuthComplete}
                    onClose={() => setAuthMode("login")}
                />
            </>
        );
    }

    // 로그인 상태: 기존 홈 화면
    return (
        <div className="app-shell">
            <header className="auth-account-bar">
                <strong>약쏙해줘</strong>

                <div className="user-session">
                    <span>{user.nickname}님</span>

                    <button
                        type="button"
                        onClick={handleLogout}
                        disabled={loggingOut}
                    >
                        {loggingOut ? "로그아웃 중..." : "로그아웃"}
                    </button>
                </div>
            </header>

            {error && <p role="alert">{error}</p>}

            <HomePage
                userId={user.userId}
                onOpenNotifications={() => {}}
            />
        </div>
    );
}
