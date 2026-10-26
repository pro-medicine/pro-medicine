import { useEffect, useRef } from "react";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function useModalFocus(onClose) {
  const ref = useRef(null);
  useEffect(() => {
    const prev = document.activeElement; // 모달을 연 버튼
    const node = ref.current;

    // 최초 포커스: autoFocus 요소가 있으면 유지, 없으면 첫 포커스 가능 요소
    if (node && !node.contains(document.activeElement)) {
      (node.querySelector(FOCUSABLE) ?? node).focus();
    }

    const onKeyDown = (e) => {
      if (e.key === "Escape") {
        onClose();
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
      prev?.focus?.(); // 닫힐 때 복원
    };
  }, []);
  return ref;
}
