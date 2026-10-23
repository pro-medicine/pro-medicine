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
  // const [showNotifications, setShowNotifications] = useState(false);

  return (
    <>
      {tab === "home" && (
        <HomePage userId={userId} onOpenNotifications={() => {}} />
      )}
      {tab === "friends" && <FriendScreen onNotifications={() => {}} />}
      {tab === "disposal" && <DisposalMapScreen onNotifications={() => {}} />}
      {tab === "my" && <ProfileScreen />}

      <BottomNav tab={tab} onChange={setTab} />

      {/* {showNotifications && (
        <NotificationPanel onClose={() => setShowNotifications(false)} />
      )} */}
    </>
  );
}
