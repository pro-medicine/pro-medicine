import { useMemo, useState } from "react";
import { BottomNav, NotificationPanel } from "./common/AppChrome";
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import { HomeScreen, SurveyModal } from "./home/HomeFeature";
import MyPage from "./mypage/MyPage";

export default function App() {
  const [tab, setTab] = useState("home");
  const [selectedFriend, setSelectedFriend] = useState(null);
  const [completed, setCompleted] = useState([1]);
  const [surveyOpen, setSurveyOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const content = useMemo(() => {
    const openNotifications = () => setNotificationsOpen(true);

    if (selectedFriend) {
      return (
        <MyPage
          friend={selectedFriend}
          onBack={() => setSelectedFriend(null)}
          onNotifications={openNotifications}
        />
      );
    }

    if (tab === "friends") {
      return (
        <FriendsPage
          onSelect={setSelectedFriend}
          onNotifications={openNotifications}
        />
      );
    }

    if (tab === "my") {
      return <MyPage onNotifications={openNotifications} />;
    }

    if (tab === "disposal") {
      return <DisposalMapPage onNotifications={openNotifications} />;
    }

    return (
      <HomeScreen
        completed={completed}
        onNotifications={openNotifications}
        onSurvey={() => setSurveyOpen(true)}
        toggleComplete={(id) =>
          setCompleted((items) =>
            items.includes(id)
              ? items.filter((item) => item !== id)
              : [...items, id],
          )
        }
      />
    );
  }, [completed, selectedFriend, tab]);

  const changeTab = (nextTab) => {
    setSelectedFriend(null);
    setTab(nextTab);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="app-shell">
      <main>{content}</main>

      {!selectedFriend && <BottomNav tab={tab} onChange={changeTab} />}

      {surveyOpen && <SurveyModal onClose={() => setSurveyOpen(false)} />}

      {notificationsOpen && (
        <NotificationPanel onClose={() => setNotificationsOpen(false)} />
      )}
    </div>
  );
}
