import { useCallback, useEffect, useRef, useState } from "react";

// 인증 (#52)
import AuthPage from "./auth/AuthPage";
import { getCurrentUser, logout, SESSION_EXPIRED_EVENT } from "./auth/authApi";

// 알림
import {
  getNotifications,
  getUnreadCount,
  subscribeNotifications,
} from "./api/notificationApi";
import NotificationToast from "./common/NotificationToast";
import ChatLauncher from "./chat";
import { BottomNav, TopNav } from "./common/AppChrome";
import NotificationPanel from "./common/NotificationPanel";
import { UnreadNotificationContext } from "./common/unreadNotificationContext";
import { isExpired, msUntilNextExpiry } from "./common/notificationTypes";

// 화면
import DisposalMapPage from "./disposal/DisposalMapPage";
import FriendsPage from "./friends/FriendsPage";
import RecommendationModal from "./recommendation";
import SignupHealthFollowup from "./recommendation/SignupHealthFollowup";
import HomePage from "./home/HomePage";
import MyPage from "./mypage/MyPage";
import LandingPage from "./landing/LandingPage";
import { restoreSurveyFocus } from "./recommendation/surveyFocus";

const NAV_STATE_KEY = "yakssokView";
const NAV_DEPTH_KEY = "yakssokDepth";
const VALID_TABS = new Set(["home", "friends", "disposal", "my"]);

function recordView(view, { replace = false, reset = false } = {}) {
  const previous = window.history.state;
  const state = previous && typeof previous === "object" ? previous : {};
  const depth = reset ? 0 : replace ? (state[NAV_DEPTH_KEY] ?? 0) : (state[NAV_DEPTH_KEY] ?? 0) + 1;
  window.history[replace ? "replaceState" : "pushState"](
    { ...state, [NAV_STATE_KEY]: view, [NAV_DEPTH_KEY]: depth },
    "",
    window.location.href,
  );
}

export default function App() {
  // 인증 상태 (#52)
  const [user, setUser] = useState(null);
  const [authMode, setAuthMode] = useState(null); // null = 서비스 소개 화면, "login" | "signup" = 로그인·회원가입 화면
  const [loading, setLoading] = useState(true);
  const [authCheckError, setAuthCheckError] = useState(false);
  const [authCheckAttempt, setAuthCheckAttempt] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [authNotice, setAuthNotice] = useState(""); // 로그인 화면에 보여줄 안내 (세션 만료 등)
  const [pendingTab, setPendingTab] = useState("home"); // 소개 화면에서 고른 화면 (로그인 후 이동)
  const authCompletionRef = useRef(null);
  const authAttemptRef = useRef(0);

  // 화면 상태
  const [tab, setTab] = useState("home");
  const [surveyOpen, setSurveyOpen] = useState(false);
  // 설문이 열려 있었는지 (뒤로 가기로 설문을 닫을 때 홈을 다시 조회하려고 popstate에서 읽음)
  const surveyOpenRef = useRef(false);
  const surveyTriggerRef = useRef(null);

  useEffect(() => {
    surveyOpenRef.current = surveyOpen;
    if (surveyOpen) return;
    const trigger = surveyTriggerRef.current;
    // Keep connected triggers for surveys reopened through browser history.
    if (trigger && !trigger.isConnected) surveyTriggerRef.current = null;
    return restoreSurveyFocus(trigger);
  }, [surveyOpen]);
  const [postSignupHealth, setPostSignupHealth] = useState(false);
  const [skipConsentAfterSignup, setSkipConsentAfterSignup] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const notificationsOpenRef = useRef(false); // SSE 콜백에서 패널이 열려 있는지 확인용
  const [toast, setToast] = useState(null); // 새 알림 팝업에 보여줄 알림
  const lastToastIdRef = useRef(null); // 같은 알림을 두 번 띄우지 않게
  const [friendTarget, setFriendTarget] = useState(null); // 알림에서 바로 열 친구 상세 (친구 userId)
  const [notificationAnchor, setNotificationAnchor] = useState(null); // 패널을 띄울 위치 (종 버튼 아래)
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false); // 상단 종 아이콘 빨간 점
  // 홈 타임라인에서 복용 체크·해제한 뒤: 빨간 점·알림 목록만 다시 조회 (홈은 이미 화면에 반영돼 있어 다시 부르지 않음)
  const [notificationRefreshKey, setNotificationRefreshKey] = useState(0);
  const refreshNotifications = () => setNotificationRefreshKey((key) => key + 1);
  const [homeRefreshKey, setHomeRefreshKey] = useState(0);
  const [friendsKey, setFriendsKey] = useState(0);
  const [myStartAdding, setMyStartAdding] = useState(false); // 홈 "영양제 등록하기" → 마이페이지 등록 화면 바로 열기
  const childBackRef = useRef(null);
  const [childBackTab, setChildBackTab] = useState(null);
  const [historyChild, setHistoryChild] = useState(null);
  const registerChildBack = useCallback((ownerTab, close, childId) => {
    if (close && childId) {
      childBackRef.current = { tab: ownerTab, close, id: childId };
      setChildBackTab(ownerTab);
      const view = window.history.state?.[NAV_STATE_KEY];
      if (view?.kind === "member" && view.tab === ownerTab &&
          (view.child?.type !== "panel" || view.child.id !== childId)) {
        recordView({ kind: "member", tab: ownerTab, child: { type: "panel", id: childId } },
          { replace: view.child?.type === "panel" });
      }
    } else if (childBackRef.current?.tab === ownerTab) {
      childBackRef.current = null;
      setChildBackTab(null);
    }
  }, []);

  const userId = user?.userId;
  const signupFollowupOwnerRef = useRef(null);

  useEffect(() => {
    signupFollowupOwnerRef.current = postSignupHealth && userId != null
      ? { userId, authAttempt: authAttemptRef.current }
      : null;
    return () => {
      signupFollowupOwnerRef.current = null;
    };
  }, [postSignupHealth, userId]);

  // 화면 상태 초기화 (로그아웃·세션 만료)
  const resetScreens = () => {
    signupFollowupOwnerRef.current = null;
    setTab("home");
    setSurveyOpen(false);
    setPostSignupHealth(false);
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setNotificationAnchor(null);
    setToast(null);
    setFriendTarget(null);
    setMyStartAdding(false);
    childBackRef.current = null;
    setChildBackTab(null);
    setHistoryChild(null);
    setHasUnreadNotifications(false);
    setHomeRefreshKey(0);
  };

  // 리프레시 토큰 갱신에 실패하면 한 번만 로그아웃 처리 (재시도 반복 없음)
  useEffect(() => {
    const handleExpired = () => {
      setUser(null);
      resetScreens();
      setAuthMode("login");
      setPendingTab("home");
      recordView({ kind: "auth", mode: "login", tab: "home" }, { replace: true, reset: true });
      setAuthNotice("로그인 세션이 만료되었어요. 다시 로그인해 주세요.");
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpired);
  }, []);

  // 최초 인증 확인: 401(로그인 필요)과 전송 오류(확인 불가)를 구분한다.
  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((currentUser) => {
        if (!active) return;
        setUser(currentUser);
        setAuthCheckError(false);
      })
      .catch(() => {
        if (active) setAuthCheckError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [authCheckAttempt]);

  useEffect(() => {
    if (loading || authCheckError) return;
    const view = window.history.state?.[NAV_STATE_KEY];
    if (user) {
      if (view?.kind === "member" || view?.kind === "memberSurvey") {
        const nextTab = VALID_TABS.has(view.tab) ? view.tab : "home";
        const nextChild = view.kind === "member" ? view.child ?? null : null;
        setTab(nextTab);
        setHistoryChild(nextChild);
        setFriendTarget(nextTab === "friends" && nextChild?.type === "friend" ? nextChild.id : null);
        setSurveyOpen(view.kind === "memberSurvey");
      } else {
        recordView({ kind: "member", tab: "home" }, { replace: true, reset: true });
      }
    } else if (view?.kind === "auth" && (view.mode === "login" || view.mode === "signup")) {
      setAuthMode(view.mode);
      setPendingTab(VALID_TABS.has(view.tab) ? view.tab : "home");
      setSurveyOpen(false);
    } else if (view?.kind === "guestSurvey") {
      setAuthMode(null);
      setSurveyOpen(true);
    } else {
      setAuthMode(null);
      setSurveyOpen(false);
      if (view?.kind !== "landing") {
        recordView({ kind: "landing" }, { replace: true, reset: true });
      }
    }
  }, [loading, authCheckError]);

  useEffect(() => {
    const handlePopState = (event) => {
      const view = event.state?.[NAV_STATE_KEY];
      const completion = authCompletionRef.current;
      if (completion) {
        authCompletionRef.current = null;
        signupFollowupOwnerRef.current = null;
        setUser(completion.account);
        setAuthMode(null);
        setAuthNotice("");
        setHasUnreadNotifications(false);
        setHomeRefreshKey(0);
        setTab(completion.tab);
        setSurveyOpen(false);
        setPostSignupHealth(completion.fromGuestV1);
        recordView({ kind: "member", tab: completion.tab }, { replace: true, reset: true });
        setPendingTab("home");
        window.scrollTo({ top: 0 });
        return;
      }
      if (userId != null) {
        const isMember = view?.kind === "member" || view?.kind === "memberSurvey";
        const nextTab = isMember && VALID_TABS.has(view.tab) ? view.tab : "home";
        const nextChild = isMember && view.kind === "member" ? view.child ?? null : null;
        const nextSurveyOpen = isMember && view.kind === "memberSurvey";
        const childBack = childBackRef.current;
        if (childBack && (childBack.tab !== nextTab || nextChild?.type !== "panel" ||
            nextChild.id !== childBack.id)) {
          childBack.close();
        }
        // popstate는 기존 기록의 목적 화면만 복원한다. 앞으로 가기에서 새 기록을 추가하지 않는다.
        if (surveyOpenRef.current && !nextSurveyOpen) setHomeRefreshKey((key) => key + 1);
        setTab(nextTab);
        setHistoryChild(nextChild);
        setSurveyOpen(nextSurveyOpen);
        setFriendTarget(nextTab === "friends" && nextChild?.type === "friend" ? nextChild.id : null);
        setMyStartAdding(false);
        setNotificationsOpen(false);
        notificationsOpenRef.current = false;
        setToast(null);
      } else {
        setAuthMode(view?.kind === "auth" && ["login", "signup"].includes(view.mode) ? view.mode : null);
        setPendingTab(view?.kind === "auth" && VALID_TABS.has(view.tab) ? view.tab : "home");
        setSurveyOpen(view?.kind === "guestSurvey");
        setAuthNotice("");
      }
      window.scrollTo({ top: 0 });
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [userId]);

  // 안 읽은 알림이 있는지: 처음 열릴 때와 알림 패널을 닫을 때(homeRefreshKey 변경) 다시 확인
  // + 1분마다, 다른 탭에 있다가 돌아왔을 때도 확인한다.
  //   (복용 알림은 1시간, 놓침 알림은 2시간이 지나면 서버 목록에서 빠지므로 새로고침 없이 빨간 점이 꺼지게)
  useEffect(() => {
    if (userId == null) return undefined;
    let ignore = false;
    let expiryTimer = null;
    const checkUnread = () => {
      getUnreadCount(userId)
        .then((data) => {
          if (!ignore) setHasUnreadNotifications((data?.unreadCount ?? 0) > 0);
        })
        .catch((err) => console.error(err));
      // 안 읽은 복용·놓침 알림이 사라질 시각에 맞춰 한 번 더 확인 → 그 순간 빨간 점이 꺼진다
      getNotifications(userId, "INTAKE")
        .then((items) => {
          if (ignore) return;
          clearTimeout(expiryTimer);
          const unread = (items ?? []).filter((item) => !item.read);
          // 조회한 사이에 이미 만료된 알림이 섞여 왔으면 잠깐 뒤 다시 확인 (서버·브라우저 시계 차이로 반복되지 않게 1번만)
          if (!retriedExpired && unread.some((item) => isExpired(item))) {
            retriedExpired = true;
            expiryTimer = setTimeout(checkUnread, 2000);
            return;
          }
          retriedExpired = false;
          const wait = msUntilNextExpiry(unread);
          if (wait != null) expiryTimer = setTimeout(checkUnread, wait + 1000);
        })
        .catch((err) => console.error(err));
    };
    let retriedExpired = false;
    checkUnread();
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") checkUnread(); // 안 보는 탭에서는 요청하지 않음
    }, 60000);
    const onVisible = () => {
      if (document.visibilityState === "visible") checkUnread();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      ignore = true;
      clearInterval(timer);
      clearTimeout(expiryTimer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [userId, homeRefreshKey, notificationRefreshKey]);

  // 실시간 알림(SSE, 로그인한 사용자만): 새 알림 신호가 오면 빨간 점·홈·열려 있는 알림 패널을 다시 조회한다.
  // 진짜 새 알림 신호(재연결 제외)면 가장 최근 안 읽은 알림을 팝업으로 띄운다. (알림 패널이 열려 있으면 생략)
  // "refresh" 신호(복용 체크 등)는 다시 조회만 하고 팝업은 띄우지 않는다.
  useEffect(() => {
    if (userId == null) return undefined;
    return subscribeNotifications(userId, (event) => {
      setHomeRefreshKey((key) => key + 1);
      if (event?.type !== "notification" || notificationsOpenRef.current) return;
      getNotifications(userId, null)
        .then((items) => {
          const latest = (items ?? []).find((item) => !item.read);
          if (!latest || latest.notificationId === lastToastIdRef.current) return;
          lastToastIdRef.current = latest.notificationId;
          setToast(latest);
        })
        .catch((err) => console.error(err));
    });
  }, [userId]);

  // KST 자정이 지나면 홈(오늘 먹을 영양제 목록·복용 알림 카드)을 새 날짜로 함께 다시 불러온다.
  // 홈을 켜 둔 채 날짜가 바뀌어도 어제 목록에 체크해서 어제 날짜로 저장되지 않게 한다.
  useEffect(() => {
    const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
    const DAY_MS = 24 * 60 * 60 * 1000;
    let timer;
    const scheduleNextMidnight = () => {
      const now = Date.now();
      const nextMidnight = (Math.floor((now + KST_OFFSET_MS) / DAY_MS) + 1) * DAY_MS - KST_OFFSET_MS;
      timer = setTimeout(() => {
        setHomeRefreshKey((key) => key + 1);
        scheduleNextMidnight(); // 다음 날 자정도 이어서 예약
      }, nextMidnight - now + 1000);
    };
    scheduleNextMidnight();
    return () => clearTimeout(timer);
  }, []);

  // 로그인·회원가입 완료 → 소개 화면에서 고른 화면(기본 홈)으로
  const handleAuthComplete = (account, attemptId, fromGuestV1 = false) => {
    if (attemptId !== authAttemptRef.current) return;
    signupFollowupOwnerRef.current = null;
    if (window.history.state?.[NAV_STATE_KEY]?.kind === "auth" &&
        (window.history.state?.[NAV_DEPTH_KEY] ?? 0) > 0) {
      authCompletionRef.current = { account, tab: pendingTab, fromGuestV1 };
      window.history.back();
      return;
    }
    setUser(account);
    setAuthMode(null);
    setAuthNotice("");
    setHasUnreadNotifications(false);
    setHomeRefreshKey(0);
    setTab(pendingTab);
    setSurveyOpen(false);
    setPostSignupHealth(fromGuestV1);
    recordView({ kind: "member", tab: pendingTab }, { replace: true, reset: true });
    setPendingTab("home");
    window.scrollTo({ top: 0 });
  };

  // 로그아웃 → 서비스 시작 화면
  const handleLogout = async () => {
    if (loggingOut) return;
    const followupOwner = signupFollowupOwnerRef.current;
    if (followupOwner) followupOwner.logoutPending = true;
    setLoggingOut(true);
    try {
      await logout();
      setUser(null);
      resetScreens();
      setAuthNotice("");
      setAuthMode(null);
      recordView({ kind: "landing" }, { replace: true, reset: true });
    } catch (err) {
      if (followupOwner && signupFollowupOwnerRef.current === followupOwner &&
          postSignupHealth && userId === followupOwner.userId &&
          followupOwner.authAttempt === authAttemptRef.current) {
        followupOwner.logoutPending = false;
      }
      window.alert(err.message || "로그아웃에 실패했어요.");
    } finally {
      setLoggingOut(false);
    }
  };

  // 종 버튼 클릭 → 버튼 위치를 기억해서 패널을 바로 아래에 띄운다.
  const openNotifications = (event) => {
    const rect = event?.currentTarget?.getBoundingClientRect?.();
    setNotificationAnchor(
      rect
        ? { top: rect.bottom + 8, right: Math.max(16, window.innerWidth - rect.right) }
        : null,
    );
    setNotificationsOpen(true);
    notificationsOpenRef.current = true;
    setToast(null);
  };
  const closeNotifications = () => {
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setHomeRefreshKey((key) => key + 1);
  };

  const changeTab = (nextTab, { replace = false } = {}) => {
    if (!VALID_TABS.has(nextTab)) return;
    if (nextTab === tab && nextTab === "friends") setFriendsKey((k) => k + 1);
    if (nextTab !== tab) recordView({ kind: "member", tab: nextTab }, { replace });
    else if (window.history.state?.[NAV_STATE_KEY]?.child) {
      recordView({ kind: "member", tab: nextTab }, { replace: true });
    }
    if (childBackRef.current?.tab === tab) childBackRef.current.close();
    setHistoryChild(null);
    setFriendTarget(null);
    setMyStartAdding(false);
    setTab(nextTab);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleBack = () => {
    if (surveyOpen) {
      closeSurvey();
      return;
    }
    const view = window.history.state?.[NAV_STATE_KEY];
    if (view?.kind === "member" && view.tab === tab && view.child &&
        (window.history.state?.[NAV_DEPTH_KEY] ?? 0) > 0) {
      window.history.back();
    } else if (tab === "friends" && friendTarget != null) {
      setFriendTarget(null);
      recordView({ kind: "member", tab }, { replace: true });
    } else if (childBackRef.current?.tab === tab) {
      childBackRef.current.close();
      recordView({ kind: "member", tab }, { replace: true });
    }
  };

  const finishSignupFollowup = (skipConsent = false) => {
    const owner = signupFollowupOwnerRef.current;
    if (!owner || owner.logoutPending || owner.userId !== userId ||
        owner.authAttempt !== authAttemptRef.current || loggingOut) return;
    signupFollowupOwnerRef.current = null;
    setPostSignupHealth(false);
    setSkipConsentAfterSignup(skipConsent);
    setSurveyOpen(true);
    recordView({ kind: "memberSurvey", tab });
  };

  const startSignupFromGuestSurvey = () => {
    if (userId != null || !surveyOpen) return;
    surveyTriggerRef.current = null;
    setSurveyOpen(false);
    setPendingTab("home");
    setAuthNotice("");
    setAuthMode("signup");
    recordView({ kind: "auth", mode: "signup", tab: "home" }, { replace: true });
    window.scrollTo({ top: 0 });
  };

  const openSurvey = (event) => {
    surveyTriggerRef.current = event?.currentTarget ?? null;
    setSkipConsentAfterSignup(false);
    setSurveyOpen(true);
    recordView(userId == null ? { kind: "guestSurvey" } : { kind: "memberSurvey", tab });
  };

  const closeSurvey = () => {
    setSkipConsentAfterSignup(false);
    const kind = window.history.state?.[NAV_STATE_KEY]?.kind;
    if ((kind === "guestSurvey" || kind === "memberSurvey") &&
        (window.history.state?.[NAV_DEPTH_KEY] ?? 0) > 0) {
      window.history.back();
    } else {
      setSurveyOpen(false);
      setHomeRefreshKey((key) => key + 1); // 설문 답이 오늘 영양소 권장량·주의 문구에 쓰여서 홈 다시 조회
      recordView(userId == null ? { kind: "landing" } : { kind: "member", tab }, { replace: true });
    }
  };

  // 알림에서 복용 완료 · 친구 신청 수락/거절을 처리한 뒤: 빨간 점·홈·알림 목록 다시 조회, 친구 화면이면 새로 그림
  const refreshAfterNotificationAction = () => {
    setHomeRefreshKey((key) => key + 1);
    if (tab === "friends") {
      setFriendTarget(null); // 예전에 알림으로 연 친구 상세가 다시 열리지 않게
      setFriendsKey((k) => k + 1);
    }
  };

  // 알림 패널의 버튼 → 해당 화면으로 이동 (안부지킴이: 친구 상세 / 복용 알림: 홈)
  const navigateFromNotification = ({ tab: nextTab, friendUserId }) => {
    setNotificationsOpen(false);
    notificationsOpenRef.current = false;
    setToast(null);
    changeTab(nextTab);
    if (nextTab === "friends" && friendUserId != null) {
      recordView({ kind: "member", tab: "friends", child: { type: "friend", id: friendUserId } });
      setFriendTarget(friendUserId);
      setFriendsKey((k) => k + 1); // 친구 화면을 새로 열어서 바로 상세로
    }
  };

  const renderContent = () => {
    switch (tab) {
      case "friends":
        return (
          <FriendsPage
            key={friendsKey}
            userId={userId}
            openFriendUserId={friendTarget}
            onSelectFriend={(friend) => {
              if (friend?.userId == null) return;
              recordView({ kind: "member", tab: "friends", child: { type: "friend", id: friend.userId } });
              setFriendTarget(friend.userId);
              window.scrollTo({ top: 0 });
            }}
            onCloseFriend={handleBack}
            restoreChild={tab === "friends" && historyChild?.type === "panel" ? historyChild.id : null}
            onRegisterBack={registerChildBack}
            onNotifications={openNotifications}
          />
        );
      case "my":
        return (
          <MyPage
            key={myStartAdding ? "my-add" : "my"}
            userId={userId}
            account={user}
            startAdding={myStartAdding}
            restoreChild={tab === "my" && historyChild?.type === "panel" ? historyChild.id : null}
            onRegisterBack={registerChildBack}
            onUserUpdated={(updatedUser) =>
              setUser((prev) => (prev?.userId === userId ? { ...prev, ...updatedUser } : prev))
            }
            onLogout={handleLogout}
            loggingOut={loggingOut}
            onNotifications={openNotifications}
          />
        );
      case "disposal":
        return <DisposalMapPage onNotifications={openNotifications}
          restoreChild={historyChild?.type === "panel" ? historyChild.id : null}
          onRegisterBack={registerChildBack} />;
      default:
        return (
          <HomePage
            userId={userId}
            refreshKey={homeRefreshKey}
            onOpenNotifications={openNotifications}
            onIntakeChanged={refreshNotifications}
            onSurvey={openSurvey}
            onAddSupplement={() => {
              changeTab("my");
              setMyStartAdding(true);
            }}
          />
        );
    }
  };

  // 로그인 상태 확인 중
  if (loading) {
    return (
      <div className="auth-splash" role="status">
        <span className="auth-splash-mark" aria-hidden="true" />
        <p>로그인 상태를 확인하는 중...</p>
      </div>
    );
  }

  // 서버 연결 실패 등으로 로그인 여부를 확인하지 못한 경우: 로그인 화면 대신 다시 시도
  if (authCheckError) {
    return (
      <div className="auth-splash" role="alert">
        <span className="auth-splash-mark is-error" aria-hidden="true" />
        <strong>로그인 상태를 확인할 수 없어요</strong>
        <p>서버 연결 상태를 확인하고 다시 시도해 주세요.</p>
        <button
          type="button"
          className="btn btn-dark"
          onClick={() => {
            setLoading(true);
            setAuthCheckError(false);
            setAuthCheckAttempt((attempt) => attempt + 1);
          }}
        >
          다시 시도
        </button>
      </div>
    );
  }

  // 비로그인: 서비스 소개 → 로그인·회원가입
  if (!user) {
    if (authMode === null) {
      return (
        <>
          <LandingPage
            onSurvey={openSurvey}
            onStart={(nextTab = "home", mode = "signup") => {
              const destination = VALID_TABS.has(nextTab) ? nextTab : "home";
              setPendingTab(destination);
              setAuthMode(mode);
              recordView({ kind: "auth", mode, tab: destination });
              window.scrollTo({ top: 0 });
            }}
          />
          {surveyOpen && <RecommendationModal userId={null} onClose={closeSurvey} onSignup={startSignupFromGuestSurvey} />}
        </>
      );
    }
    return (
      <AuthPage
        mode={authMode}
        notice={authNotice}
        onModeChange={(mode) => {
          setAuthMode(mode);
          recordView({ kind: "auth", mode, tab: pendingTab }, { replace: true });
        }}
        onAttemptStart={() => {
          authAttemptRef.current += 1;
          return authAttemptRef.current;
        }}
        onComplete={handleAuthComplete}
        onClose={() => {
          setAuthNotice("");
          if ((window.history.state?.[NAV_DEPTH_KEY] ?? 0) > 0) {
            window.history.back();
          } else {
            setAuthMode(null);
            recordView({ kind: "landing" }, { replace: true, reset: true });
          }
        }}
      />
    );
  }

  return (
    <UnreadNotificationContext.Provider value={hasUnreadNotifications}>
      <div className="app-shell">
        <TopNav
          tab={tab}
          onChange={changeTab}
          hasUnread={hasUnreadNotifications}
          ringing={toast !== null}
          onNotifications={openNotifications}
          onBack={handleBack}
          canGoBack={surveyOpen || (tab === "friends" && friendTarget != null) || childBackTab === tab}
          onLogout={handleLogout}
          loggingOut={loggingOut}
        />
        <div className="mobile-session-bar">
          <button type="button" className="mobile-session-back" onClick={handleBack} disabled={!surveyOpen && !(tab === "friends" && friendTarget != null) && childBackTab !== tab}>
            <span aria-hidden="true">←</span> 이전
          </button>
          <button type="button" onClick={handleLogout} disabled={loggingOut}>
            {loggingOut ? "로그아웃 중..." : "로그아웃"}
          </button>
        </div>
        <main>{renderContent()}</main>

        <BottomNav tab={tab} onChange={changeTab} />
        <ChatLauncher userId={userId} />

        {postSignupHealth && (
          <SignupHealthFollowup
            onClose={() => {
              signupFollowupOwnerRef.current = null;
              setPostSignupHealth(false);
            }}
            onReady={() => finishSignupFollowup()}
            onSkip={() => finishSignupFollowup(true)}
          />
        )}
        {surveyOpen && <RecommendationModal userId={userId} onClose={closeSurvey} skipConsent={skipConsentAfterSignup} />}

        {toast && (
          <NotificationToast
            key={toast.notificationId}
            item={toast}
            onClose={() => setToast(null)}
            onOpen={() => {
              const bell = document.querySelector(".top-nav-bell");
              openNotifications(bell?.offsetParent ? { currentTarget: bell } : null);
            }}
            userId={userId}
            onNavigate={navigateFromNotification}
            onChanged={refreshAfterNotificationAction}
          />
        )}

        {notificationsOpen && (
          <NotificationPanel
            userId={userId}
            anchor={notificationAnchor}
            refreshKey={`${homeRefreshKey}-${notificationRefreshKey}`}
            onRead={() => setHomeRefreshKey((key) => key + 1)}
            onClose={closeNotifications}
            onNavigate={navigateFromNotification}
            onChanged={refreshAfterNotificationAction}
          />
        )}
      </div>
    </UnreadNotificationContext.Provider>
  );
}
