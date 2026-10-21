// 공통 파일 (App.tsx 대체) — 페이지 연결만 둔다. 수정 시 팀 협의.
import HomePage from "./home";

export default function App() {
    const userId = 1; // TODO: 로그인 연동 후 실제 사용자 id

    // TODO: 알림 패널(응진) 완성되면 연결
    const handleOpenNotifications = () => {};

    return (
        <HomePage
            userId={userId}
            onOpenNotifications={handleOpenNotifications}
        />
    );
}