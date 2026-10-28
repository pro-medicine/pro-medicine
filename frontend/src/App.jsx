import { useEffect, useState } from "react";
import { getUnreadCount, subscribeNotifications } from "./api/notificationApi";
import { BottomNav } from "./common/AppChrome";
import NotificationPanel from "./common/NotificationPanel";
import { UnreadNotificationContext } from "./common/unreadNotificationContext";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import RecommendationModal from "./recommendation";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";

export default function App() {
  const userId = 1; // TODO: 로그인 연동 후 실제 사용자 id

  const [tab, setTab] = useState("home");
  const [surveyOpen, setSurveyOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationAnchor, setNotificationAnchor] = useState(null); // 패널을 띄울 위치 (종 버튼 아래)
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false); // 상단 종 아이콘 빨간 점
  const [homeRefreshKey, setHomeRefreshKey] = useState(0);
  const [friendsKey, setFriendsKey] = useState(0);

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
  useEffect(
    () => subscribeNotifications(userId, () => setHomeRefreshKey((key) => key + 1)),
    [userId],
  );

  // 종 버튼 클릭 → 버튼 위치를 기억해서 패널을 바로 아래에 띄운다.
  const openNotifications = (event) => {
    const rect = event?.currentTarget?.getBoundingClientRect?.();
    setNotificationAnchor(
      rect
        ? { top: rect.bottom + 8, right: Math.max(16, window.innerWidth - rect.right) }
        : null,
    );
    setNotificationsOpen(true);
  };
  const closeNotifications = () => {
    setNotificationsOpen(false);
    setHomeRefreshKey((key) => key + 1);
  };

  const changeTab = (nextTab) => {
    if (nextTab === tab && nextTab === "friends") setFriendsKey((k) => k + 1);
    setTab(nextTab);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const renderContent = () => {
    switch (tab) {
      case "friends":
        return (
          <FriendsPage key={friendsKey} onNotifications={openNotifications} />
        );
      case "my":
        return <MyPage onNotifications={openNotifications} />;
      case "disposal":
        return <DisposalMapPage onNotifications={openNotifications} />;
      default:
        return (
          <HomePage
            userId={userId}
            refreshKey={homeRefreshKey}
            onOpenNotifications={openNotifications}
            onSurvey={() => setSurveyOpen(true)}
          />
        );
    }
  };

  return (
    <UnreadNotificationContext.Provider value={hasUnreadNotifications}>
    <div className="app-shell">
      <main>{renderContent()}</main>

      <BottomNav tab={tab} onChange={changeTab} />

      {surveyOpen && <RecommendationModal userId={userId} onClose={() => setSurveyOpen(false)} />}

      {notificationsOpen && (
        <NotificationPanel
          userId={userId}
          anchor={notificationAnchor}
          refreshKey={homeRefreshKey}
          onRead={() => setHomeRefreshKey((key) => key + 1)}
          onClose={closeNotifications}
        />
      )}
    </div>
    </UnreadNotificationContext.Provider>
  );
}
