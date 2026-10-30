import { useEffect, useRef, useState } from "react";
import {
  getNotifications,
  getUnreadCount,
  subscribeNotifications,
} from "./api/notificationApi";
import NotificationToast from "./common/NotificationToast";
import DevNotificationTester from "./common/DevNotificationTester";
import { BottomNav, TopNav } from "./common/AppChrome";
import NotificationPanel from "./common/NotificationPanel";
import { UnreadNotificationContext } from "./common/unreadNotificationContext";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import RecommendationModal from "./recommendation";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";
import LandingPage from "./landing/LandingPage";

// 서비스 소개(첫 화면)를 지나 앱에 들어왔는지 (새로고침해도 같은 탭에서는 유지)
// TODO: 로그인(#52) 연동 후에는 로그인 여부로 판단
const STARTED_KEY = "yakssok-started";
function readStarted() {
  try {
    return sessionStorage.getItem(STARTED_KEY) === "1";
  } catch {
    return false;
  }
}

export default function App() {
  const userId = 1; // TODO: 로그인 연동 후 실제 사용자 id

  const [started, setStarted] = useState(readStarted); // false면 서비스 소개 화면
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

  // 안 읽은 알림이 있는지: 처음 열릴 때와 알림 패널을 닫을 때(homeRefreshKey 변경) 다시 확인
  useEffect(() => {
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

  // 실시간 알림(SSE): 새 알림 신호가 오면 빨간 점·홈·열려 있는 알림 패널을 다시 조회한다.
  // 진짜 새 알림 신호(재연결 제외)면 가장 최근 안 읽은 알림을 팝업으로 띄운다. (알림 패널이 열려 있으면 생략)
  useEffect(
    () =>
      subscribeNotifications(userId, (event) => {
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
      }),
    [userId],
  );

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
            openFriendUserId={friendTarget}
            onNotifications={openNotifications}
          />
        );
      case "my":
        return (
          <MyPage
            key={myStartAdding ? "my-add" : "my"}
            startAdding={myStartAdding}
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

  // 서비스 소개 화면 → "시작하기"를 누르면 앱으로 (지금은 로그인 없이 바로 홈)
  const start = (nextTab = "home") => {
    try {
      sessionStorage.setItem(STARTED_KEY, "1");
    } catch {
      // 저장이 안 되는 환경이면 이번 화면에서만 유지
    }
    setTab(nextTab);
    setStarted(true);
    window.scrollTo({ top: 0 });
  };

  if (!started) {
    return <LandingPage onStart={start} />;
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

      {surveyOpen && <RecommendationModal userId={userId} onClose={() => setSurveyOpen(false)} />}

      {/* 개발 중에만: 새 알림 팝업 테스트 버튼 (빌드에는 포함되지 않음) */}
      {import.meta.env.DEV && <DevNotificationTester userId={userId} onFake={setToast} />}

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
