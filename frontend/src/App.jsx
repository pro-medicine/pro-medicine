
import { useEffect, useState } from "react";

// 인증 기능
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout } from "./auth/authApi";

// 기존 화면 및 공통 기능
import { BottomNav, NotificationPanel } from "./common/AppChrome";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import { SurveyModal } from "./home/HomeFeature";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";
import "./auth/auth.css";

export default function App() {
    // ===========================
    // 1. 사용자 인증 상태
    // ===========================
    const [user, setUser] = useState(null);
    const [authMode, setAuthMode] = useState("login");
    const [loading, setLoading] = useState(true);
    const [loggingOut, setLoggingOut] = useState(false);
    const [error, setError] = useState("");

    // ===========================
    // 2. 기존 화면 상태
    // ===========================
    const [tab, setTab] = useState("home");
    const [surveyOpen, setSurveyOpen] = useState(false);
    const [notificationsOpen, setNotificationsOpen] = useState(false);
    const [friendsKey, setFriendsKey] = useState(0);

    // ===========================
    // 3. 최초 로그인 상태 확인
    // ===========================
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
                    setError("로그인 상태 확인에 실패했습니다.");
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

    // ===========================
    // 4. 로그인 및 회원가입 성공
    // ===========================
    const handleAuthComplete = (account) => {
        setUser(account);
        setAuthMode("login");
        setTab("home");
        setError("");
    };

    // ===========================
    // 5. 로그아웃
    // ===========================
    const handleLogout = async () => {
        if (loggingOut) return;

        setLoggingOut(true);
        setError("");

        try {
            await logout();
        } catch (err) {
            setError(err.message || "로그아웃에 실패했습니다.");
        } finally {
            setUser(null);
            setAuthMode("login");
            setTab("home");
            setSurveyOpen(false);
            setNotificationsOpen(false);
            setLoggingOut(false);
        }
    };

    const renderSessionActions = () => (
        <div className="auth-session-actions">
    <span className="auth-session-name">
      {user.nickname}님
    </span>

            <button
                type="button"
                className="auth-logout-button"
                onClick={handleLogout}
                disabled={loggingOut}
            >
                {loggingOut ? "로그아웃 중..." : "로그아웃"}
            </button>
        </div>
    );

    // ===========================
    // 6. 기존 알림 및 탭 기능
    // ===========================
    const openNotifications = () => {
        setNotificationsOpen(true);
    };

    const changeTab = (nextTab) => {
        if (nextTab === tab && nextTab === "friends") {
            setFriendsKey((key) => key + 1);
        }

        setTab(nextTab);

        window.scrollTo({
            top: 0,
            behavior: "smooth",
        });
    };

    // ===========================
    // 7. 탭별 화면 표시
    // ===========================
    const renderContent = () => {
        const userId = user.userId;

        switch (tab) {
            case "friends":
                return (
                    <FriendsPage
                        key={friendsKey}
                        onNotifications={openNotifications}
                    />
                );

            case "my":
                return (
                    <MyPage
                        onNotifications={openNotifications}
                    />
                );

            case "disposal":
                return (
                    <DisposalMapPage
                        onNotifications={openNotifications}
                    />
                );

            default:
                return (
                    <HomePage
                        userId={user.userId}
                        onOpenNotifications={openNotifications}
                        onSurvey={() => setSurveyOpen(true)}
                        headerActions={renderSessionActions()}
                    />
                );
        }
    };

    // ===========================
    // 8. 인증 상태 확인 중
    // ===========================
    if (loading) {
        return (
            <div className="auth-page" role="status">
                로그인 상태를 확인하는 중...
            </div>
        );
    }

    // ===========================
    // 9. 로그인하지 않은 사용자
    // ===========================
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

    // ===========================
    // 10. 로그인한 사용자
    // ===========================
    return (
        <div className="app-shell">
            {/* 인증 관련 오류 메시지 */}
            {error && (
                <p role="alert" className="auth-error-message">
                    {error}
                </p>
            )}

            {/* 홈이 아닌 다른 탭에서 로그아웃 표시 */}
            {tab !== "home" && (
                <div className="auth-secondary-bar">
                    {renderSessionActions()}
                </div>
            )}

            {/* 구현한 기존 화면 */}
            <main>{renderContent()}</main>

            {/* 하단 탭 */}
            <BottomNav
                tab={tab}
                onChange={changeTab}
            />

            {/* 영양제 추천 설문 */}
            {surveyOpen && (
                <SurveyModal
                    onClose={() => setSurveyOpen(false)}
                />
            )}

            {/* 알림 패널 */}
            {notificationsOpen && (
                <NotificationPanel
                    onClose={() => setNotificationsOpen(false)}
                />
            )}
        </div>
    );
}
