// 대화창 패널 (담당: 영준) — 제목 + 말풍선 목록 + 입력창. 상태는 ChatLauncher의 useChatConversation이 들고 있다
import { useEffect, useRef, useState } from "react";
import { useModalFocus } from "../common/useModalFocus";
import ChatMessage from "./ChatMessage";

const MAX_LENGTH = 300;

/**
 * props
 * - chat: useChatConversation 반환값
 * - onClose: 닫기 (✕, Esc)
 * - openerRef: 패널을 연 버튼 (닫으면 포커스를 돌려준다)
 */
export default function ChatPanel({ chat, onClose, openerRef }) {
  const dialogRef = useModalFocus(onClose, openerRef);
  const listRef = useRef(null);
  const [text, setText] = useState("");
  const { messages, sending, inputDisabled, ingredients, send, choose } = chat;

  // 새 말풍선이 오면 맨 아래로
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages.length, sending]);

  const submit = (e) => {
    e.preventDefault();
    if (!text.trim() || sending || inputDisabled) return;
    send(text);
    setText("");
  };

  const lastBot = [...messages].reverse().find((m) => m.from === "bot");

  return (
    <section
      className="chat-panel"
      role="dialog"
      aria-modal="false"
      aria-labelledby="chat-panel-title"
      ref={dialogRef}
      tabIndex={-1}
    >
      <header className="chat-header">
        <div>
          <span className="chat-eyebrow">AI 영양 상담</span>
          <h2 id="chat-panel-title">약쏙 AI 영양 상담</h2>
        </div>
        <button type="button" className="chat-close" aria-label="대화창 닫기" onClick={onClose}>
          ×
        </button>
      </header>

      <ol className="chat-list" ref={listRef} aria-live="polite">
        {messages.map((m) => (
          <ChatMessage
            key={m.id}
            message={m}
            active={m === lastBot && !sending && !chat.pending}
            ingredients={ingredients}
            onChoose={(msg, chip) => {
              choose(msg, chip);
            }}
          />
        ))}
        {sending && (
          <li className="chat-row bot">
            <span className="chat-avatar" aria-hidden="true">
              약
            </span>
            <div className="chat-bubble">
              <span className="chat-dots" role="status" aria-label="입력 중">
                <i />
                <i />
                <i />
              </span>
            </div>
          </li>
        )}
      </ol>

      <form className="chat-input" onSubmit={submit}>
        <label className="chat-sr-only" htmlFor="chat-input-text">
          고민 입력
        </label>
        <textarea
          id="chat-input-text"
          data-autofocus
          rows={2}
          maxLength={MAX_LENGTH}
          value={text}
          disabled={inputDisabled}
          placeholder={inputDisabled ? "" : "요즘 불편한 점을 적어 주세요"}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) submit(e);
          }}
        />
        <div className="chat-input-foot">
          <small className="chat-count">
            {text.length}/{MAX_LENGTH}
          </small>
          <button type="submit" className="chat-send" disabled={inputDisabled || sending || !text.trim()}>
            보내기
          </button>
        </div>
      </form>
    </section>
  );
}
