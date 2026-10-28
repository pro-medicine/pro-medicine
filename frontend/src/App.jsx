
import { useEffect, useState } from "react";

// 인증
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout } from "./auth/authApi";
import "./auth/auth.css";

// 알림
import { getUnreadCount } from "./api/notificationApi";
import { BottomNav } from "./common/AppChrome";
import NotificationPanel from "./common/NotificationPanel";
import { UnreadNotificationContext } from "./common/unreadNotificationContext";

// 기존 화면
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";
import RecommendationModal from "./recommendation";

export default function App() {
    // 인증 상태
    const [user, setUser] = useState(null);
    const [authMode, setAuthMode] = useState("login");
    const [loading, setLoading] = useState(true);
    const [loggingOut, setLoggingOut] = useState(false);
    const [error, setError] = useState("");

    // 화면 상태
    const [tab, setTab] = useState("home");
    const [surveyOpen, setSurveyOpen] = useState(false);
    const [notificationsOpen, setNotificationsOpen] = useState(false);
    const [notificationAnchor, setNotificationAnchor] = useState(null);
    const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false);
    const [homeRefreshKey, setHomeRefreshKey] = useState(0);
    const [friendsKey, setFriendsKey] = useState(0);

    const userId = user?.userId;

    // 최초 로그인 확인
    useEffect(() => {
        let active = true;

        getCurrentUser()
            .then((currentUser) => {
                if (active) setUser(currentUser);
            })
            .catch(() => {
                if (active) {
                    setError("로그인 상태 확인에 실패했습니다.");
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, []);

    // 로그인 사용자 알림 조회
    useEffect(() => {
        if (userId == null) {
            return;
        }

        let ignore = false;

        getUnreadCount(userId)
            .then((data) => {
                if (!ignore) {
                    setHasUnreadNotifications(
                        (data?.unreadCount ?? 0) > 0
                    );
                }
            })
            .catch((err) => console.error(err));

        return () => {
            ignore = true;
        };
    }, [userId, homeRefreshKey]);

    // 로그인·회원가입 완료
    const handleAuthComplete = (account) => {
        setUser(account);
        setAuthMode("login");
        setTab("home");
        setError("");
        setHasUnreadNotifications(false);
        setHomeRefreshKey(0);
    };

    // 로그아웃
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
            setNotificationAnchor(null);
            setHasUnreadNotifications(false);
            setHomeRefreshKey(0);
            setLoggingOut(false);
        }
    };

    const renderSessionActions = () => (
        <div className="auth-session-actions">
      <span className="auth-session-name">
        {user?.nickname}님
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

    // 알림 열기: 버튼 위치 계산
    const openNotifications = (event) => {
        const rect =
            event?.currentTarget?.getBoundingClientRect?.();

        setNotificationAnchor(
            rect
                ? {
                    top: rect.bottom + 8,
                    right: Math.max(
                        16,
                        window.innerWidth - rect.right
                    ),
                }
                : null
        );

        setNotificationsOpen(true);
    };

    const closeNotifications = () => {
        setNotificationsOpen(false);
        setHomeRefreshKey((key) => key + 1);
    };

    const changeTab = (nextTab) => {
        if (nextTab === tab && nextTab === "friends") {
            setFriendsKey((key) => key + 1);
        }

        setTab(nextTab);
        window.scrollTo({ top: 0, behavior: "smooth" });
    };

    const renderContent = () => {
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
                    <MyPage onNotifications={openNotifications} />
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
                        userId={userId}
                        refreshKey={homeRefreshKey}
                        onOpenNotifications={openNotifications}
                        onSurvey={() => setSurveyOpen(true)}
                        headerActions={renderSessionActions()}
                    />
                );
        }
    };

    // 로그인 확인 중
    if (loading) {
        return (
            <div className="auth-page" role="status">
                로그인 상태를 확인하는 중...
            </div>
        );
    }

    // 비로그인 화면
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

    return (
        <UnreadNotificationContext.Provider
            value={hasUnreadNotifications}
        >
            <div className="app-shell">
                {error && (
                    <p role="alert" className="auth-error-message">
                        {error}
                    </p>
                )}

                {tab !== "home" && (
                    <div className="auth-secondary-bar">
                        {renderSessionActions()}
                    </div>
                )}

                <main>{renderContent()}</main>

                <BottomNav tab={tab} onChange={changeTab} />

                {surveyOpen && (
                    <RecommendationModal
                        userId={userId}
                        onClose={() => setSurveyOpen(false)}
                    />
                )}

                {notificationsOpen && (
                    <NotificationPanel
                        userId={userId}
                        anchor={notificationAnchor}
                        onRead={() =>
                            setHomeRefreshKey((key) => key + 1)
                        }
                        onClose={closeNotifications}
                    />
                )}
            </div>
        </UnreadNotificationContext.Provider>
    );
}
