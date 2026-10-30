
import { useEffect, useState } from "react";

// 인증
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout, SESSION_EXPIRED_EVENT } from "./auth/authApi";
import "./auth/auth.css";

// 알림
import { getUnreadCount, subscribeNotifications } from "./api/notificationApi";
import ChatLauncher from "./chat";
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
    const [authCheckError, setAuthCheckError] = useState(false);
    const [authCheckAttempt, setAuthCheckAttempt] = useState(0);
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

    // A failed cookie refresh invalidates the in-memory session once (no retry loop).
    useEffect(() => {
        const handleExpired = () => {
            setUser(null);
            setAuthMode("login");
            setTab("home");
            setSurveyOpen(false);
            setNotificationsOpen(false);
            setHasUnreadNotifications(false);
            setError("로그인 세션이 만료되었습니다. 다시 로그인해주세요.");
        };
        window.addEventListener(SESSION_EXPIRED_EVENT, handleExpired);
        return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpired);
    }, []);


    // 최초 인증 확인: 401(로그인 필요)과 전송 오류(확인 불가)를 구분한다.
    useEffect(() => {
        let active = true;

        getCurrentUser()
            .then((currentUser) => {
                if (!active) return;
                setUser(currentUser);
                setAuthCheckError(false);
            })
            .catch(() => {
                if (active) setAuthCheckError(true);
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [authCheckAttempt]);

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
            setUser(null);
            setAuthMode("login");
            setTab("home");
            setSurveyOpen(false);
            setNotificationsOpen(false);
            setNotificationAnchor(null);
            setHasUnreadNotifications(false);
            setHomeRefreshKey(0);
        } catch (err) {
            setError(err.message || "로그아웃에 실패했습니다.");
        } finally {
            setLoggingOut(false);
        }
    };
    // 사용자 정보 및 로그아웃 버튼
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

    // 실시간 알림(SSE): 새 알림 신호가 오면 빨간 점·홈·열려 있는 알림 패널을 다시 조회한다.
    // 로그인한 사용자만 실시간 알림 구독
    useEffect(() => {
        if (userId == null) {
            return;
        }

        return subscribeNotifications(userId, () => {
            setHomeRefreshKey((key) => key + 1);
        });
    }, [userId]);

    // KST 자정이 지나면 홈(오늘 먹을 영양제 목록·복용 알림 카드)을 새 날짜로 함께 다시 불러온다.
    // 홈을 켜 둔 채 날짜가 바뀌어도 어제 목록에 체크해서 어제 날짜로 저장되지 않게 한다.
    useEffect(() => {
        const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
        const DAY_MS = 24 * 60 * 60 * 1000;
        let timer;
        const scheduleNextMidnight = () => {
            const now = Date.now();
            const nextMidnight = (Math.floor((now + KST_OFFSET_MS) / DAY_MS) + 1) * DAY_MS - KST_OFFSET_MS;
            timer = setTimeout(() => {
                setHomeRefreshKey((key) => key + 1);
                scheduleNextMidnight(); // 다음 날 자정도 이어서 예약
            }, nextMidnight - now + 1000);
        };
        scheduleNextMidnight();
        return () => clearTimeout(timer);
    }, []);

    // 종 버튼 클릭 → 버튼 위치를 기억해서 패널을 바로 아래에 띄운다.

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
                        userId={userId}
                        onNotifications={openNotifications}
                    />
                );

            case "my":
                return (
                    <MyPage
                        userId={userId}
                        onUserUpdated={(updatedUser) =>
                            setUser((prev) =>
                                prev?.userId === userId
                                    ? { ...prev, ...updatedUser }
                                    : prev
                            )
                        }
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

    // 서버 연결 실패 등으로 로그인 여부를 확인하지 못한 경우, 로그인 화면을 보여주지 않는다.
    if (authCheckError) {
        return (
            <div className="auth-page" role="alert">
                <div className="auth-card" style={{ maxWidth: 440, margin: "48px auto" }}>
                    <p className="auth-error-message">
                        로그인 상태를 확인할 수 없습니다. 연결 상태를 확인하고 다시 시도해주세요.
                    </p>
                    <button
                        type="button"
                        className="auth-submit"
                        onClick={() => {
                            setLoading(true);
                            setAuthCheckError(false);
                            setAuthCheckAttempt((attempt) => attempt + 1);
                        }}
                    >
                        다시 시도
                    </button>
                </div>
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
                    <p
                        role="alert"
                        className="auth-error-message"
                    >
                        {error}
                    </p>
                )}

                {/* 홈 이외의 화면에서도 로그아웃 가능 */}
                {tab !== "home" && (
                    <div className="auth-secondary-bar">
                        {renderSessionActions()}
                    </div>
                )}

                <main>
                    {renderContent()}
                </main>

                <BottomNav
                    tab={tab}
                    onChange={changeTab}
                />

                {/* 로그인 사용자 AI 채팅 */}
                <ChatLauncher userId={userId} />

                {/* AI 추천 설문 */}
                {surveyOpen && (
                    <RecommendationModal
                        userId={userId}
                        onClose={() => setSurveyOpen(false)}
                    />
                )}

                {/* 알림 패널 */}
                {notificationsOpen && (
                    <NotificationPanel
                        userId={userId}
                        anchor={notificationAnchor}
                        refreshKey={homeRefreshKey}
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
