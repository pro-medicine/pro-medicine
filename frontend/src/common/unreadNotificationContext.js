import { createContext } from "react";

// 안 읽은 알림이 있는지 (true/false)
// App이 값을 넣고, 각 화면 상단 TopBar의 종 아이콘이 읽어서 빨간 점을 표시한다.
// Provider 밖에서는 기본값 false (빨간 점 없음)
export const UnreadNotificationContext = createContext(false);
