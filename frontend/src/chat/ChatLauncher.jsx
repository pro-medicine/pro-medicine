// 화면 우측 하단 고정 버튼 + 대화창 (담당: 영준, V4) — 모든 탭에서 같은 자리에 떠 있다
// App.jsx에는 <ChatLauncher userId={userId} /> 한 줄만 둔다. 대화 상태는 여기서 들고 있어 패널을 닫았다 열어도 유지
import { useRef, useState } from "react";
import ChatPanel from "./ChatPanel";
import { useChatConversation } from "./useChatConversation";
import "./chat.css";

export default function ChatLauncher({ userId }) {
  const [open, setOpen] = useState(false);
  const openerRef = useRef(null);
  const chat = useChatConversation(userId);

  const toggle = () => {
    if (!open) chat.open();
    setOpen((v) => !v);
  };

  return (
    <>
      {open && <ChatPanel chat={chat} onClose={() => setOpen(false)} openerRef={openerRef} />}
      <button
        type="button"
        ref={openerRef}
        className={`chat-launcher${open ? " open" : ""}`}
        aria-label={open ? "AI 영양 상담 닫기" : "AI 영양 상담 열기"}
        aria-expanded={open}
        onClick={toggle}
      >
        {open ? (
          <span className="chat-launcher-x" aria-hidden="true">
            ×
          </span>
        ) : (
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.2 3.4c-.5.4-1.3 0-1.3-.6V16A2.5 2.5 0 0 1 4 13.5v-8Z"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinejoin="round"
            />
            <circle cx="8.5" cy="9.5" r="1.1" fill="currentColor" />
            <circle cx="12" cy="9.5" r="1.1" fill="currentColor" />
            <circle cx="15.5" cy="9.5" r="1.1" fill="currentColor" />
          </svg>
        )}
      </button>
    </>
  );
}
