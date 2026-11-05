import { useEffect, useRef } from "react";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * 모달의 포커스 이동·Tab 가두기·Esc 닫기·닫힌 뒤 포커스 복원을 맡는 훅.
 * @param onClose Esc를 눌렀을 때 호출할 함수 (리스너 등록 후에도 항상 최신 함수가 호출됨)
 * @param openerRef 모달을 연 버튼을 담은 ref (부모가 클릭 시점에 저장)
 * @param options.active false면 Esc·Tab 처리를 하지 않음 (위에 다른 모달이 떠 있을 때)
 */
export function useModalFocus(onClose, openerRef, { active = true } = {}) {
  const ref = useRef(null);
  // 리스너는 마운트 때 한 번만 등록하므로, 최신 값은 ref로 읽는다
  const onCloseRef = useRef(onClose);
  const activeRef = useRef(active);
  onCloseRef.current = onClose;
  activeRef.current = active;

  useEffect(() => {
    const node = ref.current;

    if (node && !node.contains(document.activeElement)) {
      const target =
        node.querySelector("[data-autofocus]") ??
        node.querySelector(FOCUSABLE) ??
        node;
      target.focus();
    }

    const onKeyDown = (e) => {
      // 위에 다른 모달이 떠 있으면 그 모달의 훅이 처리하도록 양보
      if (!activeRef.current) return;

      if (e.key === "Escape") {
        onCloseRef.current();
        return;
      }
      if (e.key !== "Tab" || !node) return;

      const items = [...node.querySelectorAll(FOCUSABLE)];
      if (items.length === 0) {
        e.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];

      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      openerRef?.current?.focus?.(); // 저장해둔 여는 버튼으로 복원
    };
  }, []);

  return ref;
}
