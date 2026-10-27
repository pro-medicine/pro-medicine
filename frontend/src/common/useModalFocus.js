import { useEffect, useRef } from "react";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

// openerRef: 모달을 연 버튼을 담은 ref (부모가 클릭 시점에 저장)
export function useModalFocus(onClose, openerRef) {
  const ref = useRef(null);

  useEffect(() => {
    const node = ref.current;

    if (node && !node.contains(document.activeElement)) {
      const target =
        node.querySelector("[data-autofocus]") ??
        (node.querySelector(FOCUSABLE) ?? node).focus();
      target.focus();
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
      openerRef?.current?.focus?.(); // 저장해둔 여는 버튼으로 복원
    };
  }, []);

  return ref;
}
