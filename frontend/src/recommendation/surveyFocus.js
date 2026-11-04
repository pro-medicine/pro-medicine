// Run after the closing render, once the modal has been removed.
export function restoreSurveyFocus(trigger) {
  if (!trigger) return;
  const frame = requestAnimationFrame(() => {
    if (trigger.isConnected) trigger.focus();
  });
  return () => cancelAnimationFrame(frame);
}
