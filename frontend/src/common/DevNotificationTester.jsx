// 개발 중에만 보이는 "새 알림 테스트" 버튼 (pnpm dev 일 때만, 빌드된 화면에는 없음)
// 종류를 누르면 백엔드 테스트 API로 진짜 알림을 만들고 → SSE로 팝업이 뜬다.
// 백엔드 테스트 API가 꺼져 있거나 서버가 없으면 화면에서만 가짜 팝업을 띄운다.
import { useState } from "react";
import { createTestNotification } from "../api/notificationApi";

const TYPES = [
  { type: "INTAKE_REMINDER", label: "복용 알림", category: "INTAKE", title: "점심 영양제 1개를 드실 시간이에요" },
  { type: "GUARDIAN", label: "안부지킴이", category: "FRIEND", title: "테스트엄마님이 4일째 접속하지 않았어요. 안부를 확인해보세요", actorUserId: 9803 },
  { type: "FRIEND_GOAL_ACHIEVED", label: "친구 완료", category: "FRIEND", title: "테스트서준님이 오늘 복용을 모두 완료했어요" },
  { type: "NEW_FOLLOWER", label: "친구 신청", category: "FRIEND", title: "테스트하늘님이 팔로우를 요청했어요", actorUserId: 9802 },
  { type: "FOLLOW_ACCEPTED", label: "친구 수락", category: "FRIEND", title: "테스트유진님이 팔로우 요청을 수락했어요" },
];

let fakeId = -1; // 가짜 팝업 id (서버 알림과 겹치지 않게 음수)

// 화면에서만 띄울 가짜 알림 (createdAt은 한국 시간 문자열)
function fakeNotification(item) {
  return {
    notificationId: fakeId--,
    type: item.type,
    category: item.category,
    title: item.title,
    actorUserId: item.actorUserId ?? null,
    read: false,
    createdAt: new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 19),
    // 가짜 복용 알림도 "복용 완료" 버튼이 보이도록 지금 시각을 알림 시각으로
    scheduledAt: item.type === "INTAKE_REMINDER" ? new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 16) + ":00" : null,
  };
}

export default function DevNotificationTester({ userId, onFake }) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");

  const send = async (item) => {
    setMessage("");
    try {
      await createTestNotification(userId, item.type);
      setMessage("서버 알림 생성 → 팝업 대기");
    } catch {
      // 백엔드 테스트 API가 없으면 화면에서만 확인
      onFake(fakeNotification(item));
      setMessage("서버 API 꺼짐 → 화면에서만 표시");
    }
  };

  return (
    <div className="dev-noti-tester">
      {open && (
        <div className="dev-noti-menu">
          <strong>새 알림 테스트</strong>
          {TYPES.map((item) => (
            <button key={item.type} type="button" onClick={() => send(item)}>
              {item.label}
            </button>
          ))}
          {message && <small>{message}</small>}
        </div>
      )}
      <button type="button" className="dev-noti-toggle" onClick={() => setOpen((v) => !v)}>
        {open ? "닫기" : "알림 테스트"}
      </button>
    </div>
  );
}
