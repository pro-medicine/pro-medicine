import { test } from 'node:test';
import assert from 'node:assert/strict';
import { restoreSurveyFocus } from '../src/recommendation/surveyFocus.js';

 test('closing restores each landing trigger only after rendering', () => {
  let callback;
  globalThis.requestAnimationFrame = (fn) => { callback = fn; return 1; };
  globalThis.cancelAnimationFrame = () => { callback = null; };
  for (const name of ['landing entry', 'guest survey section']) {
    let focused = false;
    const trigger = { isConnected: true, focus: () => { focused = true; } };
    const cleanup = restoreSurveyFocus(trigger);
    assert.equal(focused, false, name);
    callback();
    assert.equal(focused, true, name);
    cleanup();
    assert.equal(callback, null);
  }
});

test('removed triggers and signup transitions do not receive focus', () => {
  let callback;
  globalThis.requestAnimationFrame = (fn) => { callback = fn; return 1; };
  globalThis.cancelAnimationFrame = () => {};
  restoreSurveyFocus({ isConnected: false, focus: () => assert.fail('removed trigger') });
  callback();
  callback = null;
  assert.equal(restoreSurveyFocus(null), undefined);
  assert.equal(callback, null);
});

test('landing buttons, close and Escape use the shared flow; signup clears the trigger', async () => {
  const { readFile } = await import('node:fs/promises');
  const source = async (path) => readFile(new URL(path, import.meta.url), 'utf8');
  const app = await source('../src/App.jsx');
  const landing = await source('../src/landing/LandingPage.jsx');
  const section = await source('../src/recommendation/GuestSurveySection.jsx');
  const modal = await source('../src/recommendation/RecommendationModal.jsx');
  assert.match(landing, /onClick=\{onSurvey\}/);
  assert.match(landing, /GuestSurveySection onSurvey=\{onSurvey\}/);
  assert.match(section, /onClick=\{onSurvey\}/);
  assert.match(app, /onSurvey=\{openSurvey\}/);
  assert.match(app, /surveyTriggerRef.current = event\?\.currentTarget/);
  assert.match(app, /startSignupFromGuestSurvey = \(\) => \{[^}]*surveyTriggerRef.current = null;\s*setSurveyOpen\(false\)/);
  assert.match(app, /return restoreSurveyFocus\(trigger\);\s*\}, \[surveyOpen\]\)/);
  assert.match(app, /onClose=\{closeSurvey\} onSignup=\{startSignupFromGuestSurvey\}/);
  assert.match(modal, /e.key === "Escape" && onClose\(\)/);
  assert.match(modal, /onClick=\{onClose\}/);
});

// Exercise App's actual effect body without adding a DOM/test framework dependency.
test('App retains history triggers, cancels pending focus, and clears removed triggers', async (t) => {
  const { readFile } = await import('node:fs/promises');
  const app = await readFile(new URL('../src/App.jsx', import.meta.url), 'utf8');
  const body = app.match(/useEffect\(\(\) => \{\n(    if \(surveyOpen\) return;[\s\S]*?)\n  \}, \[surveyOpen\]\)/)?.[1];
  assert.ok(body, 'survey focus effect exists');
  const effect = new Function('surveyOpen', 'surveyTriggerRef', 'restoreSurveyFocus', body);
  const frames = new Map();
  let nextFrame = 0;
  t.mock.method(globalThis, 'requestAnimationFrame', (callback) => {
    frames.set(++nextFrame, callback);
    return nextFrame;
  });
  t.mock.method(globalThis, 'cancelAnimationFrame', (frame) => frames.delete(frame));
  const flush = () => {
    const pending = [...frames.values()];
    frames.clear();
    pending.forEach((callback) => callback());
  };
  let focuses = 0;
  const trigger = { isConnected: true, focus: () => focuses++ };
  const ref = { current: trigger };
  let cleanup;
  const render = (open) => {
    cleanup?.();
    cleanup = effect(open, ref, restoreSurveyFocus);
  };
  render(true);
  for (const close of ['normal close', 'Escape', 'history back', 'close after history forward']) {
    render(false);
    flush();
    assert.equal(ref.current, trigger, close);
    render(true); // History forward reopens without invoking openSurvey.
  }
  assert.equal(focuses, 4);
  render(false);
  render(true); // Reopen before the scheduled animation frame.
  flush();
  assert.equal(focuses, 4, 'reopening cancels pending focus');
  render(false);
  trigger.isConnected = false; // Removal between commit and animation frame.
  flush();
  assert.equal(focuses, 4);
  render(true);
  render(false);
  assert.equal(ref.current, null, 'disconnected triggers are released');
  flush();
  trigger.isConnected = true;
  ref.current = trigger;
  render(true);
  ref.current = null; // startSignupFromGuestSurvey clears before closing.
  render(false);
  flush();
  assert.equal(focuses, 4, 'signup never focuses the previous screen');
  cleanup?.();
});
