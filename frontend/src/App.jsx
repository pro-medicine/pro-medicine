import { useMemo, useState } from "react";
import { BottomNav, NotificationPanel } from "./common/AppChrome";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import { HomeScreen, SurveyModal } from "./home/HomeFeature";
import MyPage from "./mypage/MyPage";

export default function App() {
  const userId = 1; // TODO: 로그인 연동 후 실제 사용자 id

  const [tab, setTab] = useState("home");
  const [surveyOpen, setSurveyOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [friendsKey, setFriendsKey] = useState(0);

  const openNotifications = () => setNotificationsOpen(true);

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
            onOpenNotifications={openNotifications}
            onSurvey={() => setSurveyOpen(true)}
          />
        );
    }
  };

  return (
    <div className="app-shell">
      <main>{renderContent()}</main>

      <BottomNav tab={tab} onChange={changeTab} />

      {surveyOpen && <SurveyModal onClose={() => setSurveyOpen(false)} />}

      {notificationsOpen && (
        <NotificationPanel onClose={() => setNotificationsOpen(false)} />
      )}
    </div>
  );
}
