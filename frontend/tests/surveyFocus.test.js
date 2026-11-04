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
