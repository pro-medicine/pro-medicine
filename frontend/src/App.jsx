import { useEffect, useRef, useState } from "react";

// 인증 (#52)
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout, SESSION_EXPIRED_EVENT } from "./auth/authApi";

// 알림
import {
  getNotifications,
  getUnreadCount,
  subscribeNotifications,
} from "./api/notificationApi";
import NotificationToast from "./common/NotificationToast";
import ChatLauncher from "./chat";
import { BottomNav, TopNav } from "./common/AppChrome";
import NotificationPanel from "./common/NotificationPanel";
import { UnreadNotificationContext } from "./common/unreadNotificationContext";

// 화면
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import RecommendationModal from "./recommendation";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";
import LandingPage from "./landing/LandingPage";

export default function App() {
  // 인증 상태 (#52)
  const [user, setUser] = useState(null);
  const [authMode, setAuthMode] = useState(null); // null = 서비스 소개 화면, "login" | "signup" = 로그인·회원가입 화면
  const [loading, setLoading] = useState(true);
  const [authCheckError, setAuthCheckError] = useState(false);
  const [authCheckAttempt, setAuthCheckAttempt] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [authNotice, setAuthNotice] = useState(""); // 로그인 화면에 보여줄 안내 (세션 만료 등)
  const [pendingTab, setPendingTab] = useState("home"); // 소개 화면에서 고른 화면 (로그인 후 이동)

  // 화면 상태
  const [tab, setTab] = useState("home");
  const [surveyOpen, setSurveyOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const notificationsOpenRef = useRef(false); // SSE 콜백에서 패널이 열려 있는지 확인용
  const [toast, setToast] = useState(null); // 새 알림 팝업에 보여줄 알림
  const lastToastIdRef = useRef(null); // 같은 알림을 두 번 띄우지 않게
  const [friendTarget, setFriendTarget] = useState(null); // 알림에서 바로 열 친구 상세 (친구 userId)
  const [notificationAnchor, setNotificationAnchor] = useState(null); // 패널을 띄울 위치 (종 버튼 아래)
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false); // 상단 종 아이콘 빨간 점
  const [homeRefreshKey, setHomeRefreshKey] = useState(0);
  const [friendsKey, setFriendsKey] = useState(0);
  const [myStartAdding, setMyStartAdding] = useState(false); // 홈 "영양제 등록하기" → 마이페이지 등록 화면 바로 열기

  const userId = user?.userId;

  // 화면 상태 초기화 (로그아웃·세션 만료)
  const resetScreens = () => {
    setTab("home");
    setSurveyOpen(false);
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setNotificationAnchor(null);
    setToast(null);
    setFriendTarget(null);
    setMyStartAdding(false);
    setHasUnreadNotifications(false);
    setHomeRefreshKey(0);
  };

  // 리프레시 토큰 갱신에 실패하면 한 번만 로그아웃 처리 (재시도 반복 없음)
  useEffect(() => {
    const handleExpired = () => {
      setUser(null);
      resetScreens();
      setAuthMode("login");
      setAuthNotice("로그인 세션이 만료되었어요. 다시 로그인해 주세요.");
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

  // 안 읽은 알림이 있는지: 처음 열릴 때와 알림 패널을 닫을 때(homeRefreshKey 변경) 다시 확인
  useEffect(() => {
    if (userId == null) return;
    let ignore = false;
    getUnreadCount(userId)
      .then((data) => {
        if (!ignore) setHasUnreadNotifications((data?.unreadCount ?? 0) > 0);
      })
      .catch((err) => console.error(err));
    return () => {
      ignore = true;
    };
  }, [userId, homeRefreshKey]);

  // 실시간 알림(SSE, 로그인한 사용자만): 새 알림 신호가 오면 빨간 점·홈·열려 있는 알림 패널을 다시 조회한다.
  // 진짜 새 알림 신호(재연결 제외)면 가장 최근 안 읽은 알림을 팝업으로 띄운다. (알림 패널이 열려 있으면 생략)
  useEffect(() => {
    if (userId == null) return undefined;
    return subscribeNotifications(userId, (event) => {
      setHomeRefreshKey((key) => key + 1);
      if (event?.type !== "notification" || notificationsOpenRef.current) return;
      getNotifications(userId, null)
        .then((items) => {
          const latest = (items ?? []).find((item) => !item.read);
          if (!latest || latest.notificationId === lastToastIdRef.current) return;
          lastToastIdRef.current = latest.notificationId;
          setToast(latest);
        })
        .catch((err) => console.error(err));
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

  // 로그인·회원가입 완료 → 소개 화면에서 고른 화면(기본 홈)으로
  const handleAuthComplete = (account) => {
    setUser(account);
    setAuthMode(null);
    setAuthNotice("");
    setHasUnreadNotifications(false);
    setHomeRefreshKey(0);
    setTab(pendingTab);
    setPendingTab("home");
    window.scrollTo({ top: 0 });
  };

  // 로그아웃 → 로그인 화면
  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
      setUser(null);
      resetScreens();
      setAuthNotice("");
      setAuthMode("login");
    } catch (err) {
      window.alert(err.message || "로그아웃에 실패했어요.");
    } finally {
      setLoggingOut(false);
    }
  };

  // 종 버튼 클릭 → 버튼 위치를 기억해서 패널을 바로 아래에 띄운다.
  const openNotifications = (event) => {
    const rect = event?.currentTarget?.getBoundingClientRect?.();
    setNotificationAnchor(
      rect
        ? { top: rect.bottom + 8, right: Math.max(16, window.innerWidth - rect.right) }
        : null,
    );
    setNotificationsOpen(true);
    notificationsOpenRef.current = true;
    setToast(null);
  };
  const closeNotifications = () => {
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setHomeRefreshKey((key) => key + 1);
  };

  const changeTab = (nextTab) => {
    if (nextTab === tab && nextTab === "friends") setFriendsKey((k) => k + 1);
    setFriendTarget(null);
    setMyStartAdding(false);
    setTab(nextTab);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // 알림에서 복용 완료 · 친구 신청 수락/거절을 처리한 뒤: 빨간 점·홈·알림 목록 다시 조회, 친구 화면이면 새로 그림
  const refreshAfterNotificationAction = () => {
    setHomeRefreshKey((key) => key + 1);
    if (tab === "friends") setFriendsKey((k) => k + 1);
  };

  // 알림 패널의 버튼 → 해당 화면으로 이동 (안부지킴이: 친구 상세 / 복용 알림: 홈)
  const navigateFromNotification = ({ tab: nextTab, friendUserId }) => {
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setToast(null);
    changeTab(nextTab);
    if (friendUserId) {
      setFriendTarget(friendUserId);
      setFriendsKey((k) => k + 1); // 친구 화면을 새로 열어서 바로 상세로
    }
  };

  const renderContent = () => {
    switch (tab) {
      case "friends":
        return (
          <FriendsPage
            key={friendsKey}
            userId={userId}
            openFriendUserId={friendTarget}
            onNotifications={openNotifications}
          />
        );
      case "my":
        return (
          <MyPage
            key={myStartAdding ? "my-add" : "my"}
            userId={userId}
            account={user}
            startAdding={myStartAdding}
            onUserUpdated={(updatedUser) =>
              setUser((prev) => (prev?.userId === userId ? { ...prev, ...updatedUser } : prev))
            }
            onLogout={handleLogout}
            loggingOut={loggingOut}
            onNotifications={openNotifications}
          />
        );
      case "disposal":
        return <DisposalMapPage onNotifications={openNotifications} />;
      default:
        return (
          <HomePage
            userId={userId}
            refreshKey={homeRefreshKey}
            onOpenNotifications={openNotifications}
            onSurvey={() => setSurveyOpen(true)}
            onAddSupplement={() => {
              changeTab("my");
              setMyStartAdding(true);
            }}
          />
        );
    }
  };

  // 로그인 상태 확인 중
  if (loading) {
    return (
      <div className="auth-splash" role="status">
        <span className="auth-splash-mark" aria-hidden="true" />
        <p>로그인 상태를 확인하는 중...</p>
      </div>
    );
  }

  // 서버 연결 실패 등으로 로그인 여부를 확인하지 못한 경우: 로그인 화면 대신 다시 시도
  if (authCheckError) {
    return (
      <div className="auth-splash" role="alert">
        <span className="auth-splash-mark is-error" aria-hidden="true" />
        <strong>로그인 상태를 확인할 수 없어요</strong>
        <p>서버 연결 상태를 확인하고 다시 시도해 주세요.</p>
        <button
          type="button"
          className="btn btn-dark"
          onClick={() => {
            setLoading(true);
            setAuthCheckError(false);
            setAuthCheckAttempt((attempt) => attempt + 1);
          }}
        >
          다시 시도
        </button>
      </div>
    );
  }

  // 비로그인: 서비스 소개 → 로그인·회원가입
  if (!user) {
    if (authMode === null) {
      return (
        <LandingPage
          onStart={(nextTab = "home", mode = "signup") => {
            setPendingTab(nextTab);
            setAuthMode(mode);
            window.scrollTo({ top: 0 });
          }}
        />
      );
    }
    return (
      <AuthPage
        mode={authMode}
        notice={authNotice}
        onModeChange={setAuthMode}
        onComplete={handleAuthComplete}
        onClose={() => {
          setAuthNotice("");
          setAuthMode(null);
        }}
      />
    );
  }

  return (
    <UnreadNotificationContext.Provider value={hasUnreadNotifications}>
      <div className="app-shell">
        <TopNav
          tab={tab}
          onChange={changeTab}
          hasUnread={hasUnreadNotifications}
          ringing={toast !== null}
          onNotifications={openNotifications}
        />
        <main>{renderContent()}</main>

        <BottomNav tab={tab} onChange={changeTab} />
        <ChatLauncher userId={userId} />

        {surveyOpen && <RecommendationModal userId={userId} onClose={() => setSurveyOpen(false)} />}

        {toast && (
          <NotificationToast
            key={toast.notificationId}
            item={toast}
            onClose={() => setToast(null)}
            onOpen={() => {
              const bell = document.querySelector(".top-nav-bell");
              openNotifications(bell?.offsetParent ? { currentTarget: bell } : null);
            }}
            userId={userId}
            onNavigate={navigateFromNotification}
            onChanged={refreshAfterNotificationAction}
          />
        )}

        {notificationsOpen && (
          <NotificationPanel
            userId={userId}
            anchor={notificationAnchor}
            refreshKey={homeRefreshKey}
            onRead={() => setHomeRefreshKey((key) => key + 1)}
            onClose={closeNotifications}
            onNavigate={navigateFromNotification}
            onChanged={refreshAfterNotificationAction}
          />
        )}
      </div>
    </UnreadNotificationContext.Provider>
  );
}
