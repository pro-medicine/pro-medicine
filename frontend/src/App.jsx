import { useState } from "react";
import HomePage from "./home";
import { BottomNav } from "./common/AppChrome"; // 실제 파일 경로에 맞게 수정
// import { NotificationPanel } from "./NotificationPanel"; // 실제 파일 경로에 맞게 수정
import FriendScreen from "./friends/FriendsPage";
import DisposalMapScreen from "./disposal/DisposalMapPage";
import ProfileScreen from "./mypage/MyPage";

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
