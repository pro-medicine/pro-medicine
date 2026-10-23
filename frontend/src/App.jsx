import { useState } from "react";
import { BottomNav, NotificationPanel } from "./common/AppChrome";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import { SurveyModal } from "./home/HomeFeature";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";

export default function App() {
  const userId = 1; // TODO: 로그인 연동 후 실제 사용자 id

  const [tab, setTab] = useState("home");
  const [selectedFriend, setSelectedFriend] = useState(null);
  const [surveyOpen, setSurveyOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const openNotifications = () => setNotificationsOpen(true);

  const changeTab = (nextTab) => {
    setSelectedFriend(null);
    setTab(nextTab);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const renderContent = () => {
    // 친구 상세 (친구 탭에서 선택했을 때)
    if (selectedFriend) {
      return (
        <MyPage
          friend={selectedFriend}
          onBack={() => setSelectedFriend(null)}
          onNotifications={openNotifications}
        />
      );
    }

    switch (tab) {
      case "friends":
        return (
          <FriendsPage
            onSelect={setSelectedFriend}
            onNotifications={openNotifications}
          />
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

      {!selectedFriend && <BottomNav tab={tab} onChange={changeTab} />}

      {surveyOpen && <SurveyModal onClose={() => setSurveyOpen(false)} />}

      {notificationsOpen && (
        <NotificationPanel onClose={() => setNotificationsOpen(false)} />
      )}
    </div>
  );
}
