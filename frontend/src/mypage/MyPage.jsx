import { useRef, useEffect, useState, useCallback } from "react";
import { Icon, TopBar } from "../shared/ui";
import {
  fetchIntakeStats,
  fetchMySupplements,
  fetchNutrition,
  fetchUser,
  removeMySupplement,
  updateShareIntake,
} from "../api/mypageApi";
import {
  getCalendarEvents,
  createCalendarEvent,
  createPrescription,
  updateCalendarEvent,
  deleteCalendarEvent,
  updateCalendarEventStatus,
  fetchInteractions,
  updatePrescription,
} from "../api/calendarEventApi";
import { deletePrescription } from "../api/prescriptionApi";
import { useModalFocus } from "../common/useModalFocus";
import ProfileEditPage from "./ProfileEditPage";
import SupplementAddPage from "./SupplementAddPage";
import WithdrawalSection from "./WithdrawalSection";
import IntakeCalendar from "../calendar/IntakeCalendar";
import { getMyCalendarProgress, getMyDayIntakes } from "../api/intakeApi";
import MyStats from "./MyStats";
import SupplementGroups from "./SupplementGroups";
import { getFriendCalendar, getFriendDayIntakes } from "../api/friendApi";
import RecentRecommendationSection from "../recommendation/RecentRecommendationSection";
import ConfirmDialog from "../friends/ConfirmDialog";
import { HomeToast } from "../home/HomeStates";
import "../home/home.css";

// 카드 병 색상: shared/ui 예시 데이터에서 쓰는 색상 이름을 순서대로 돌려 씀
const cardColors = ["sun", "ocean", "lilac"];
let nextSupplementPanelInstanceId = 0;

// 끼니 / 식전·식후 코드 → 화면 문구 (ANY = 식사와 상관없음 → 끼니만 표시)
const mealSlotLabels = { BREAKFAST: "아침", LUNCH: "점심", DINNER: "저녁" };
const mealRelationLabels = { BEFORE: "식전", AFTER: "식후", ANY: "" };

// 삭제 요청이 너무 오래 걸리면(토큰 재발급 대기 포함) 실패로 보고 확인 창을 닫음
const REMOVE_TIMEOUT_MS = 15000;

// promise가 ms 안에 끝나지 않으면 message로 실패 처리
function withTimeout(promise, ms, message) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

// 복용 일정 목록 → "아침 식후 · 저녁 식전"
function scheduleText(schedules) {
  if (schedules.length === 0) return "복용 일정 없음";
  return schedules
    .map((schedule) => {
      const slot = mealSlotLabels[schedule.mealSlot] ?? schedule.mealSlot;
      const relation =
        mealRelationLabels[schedule.mealRelation] ?? schedule.mealRelation;
      return relation ? `${slot} ${relation}` : slot;
    })
    .join(" · ");
}

// API 응답 1개 → SupplementCard 가 받는 모양으로 변환
// 직접 입력한 제품은 제품 정보가 없어서 이름만 보여줌
function toSupplementCard(item, index) {
  // 직접 입력: 검색 제품(catalogProductId)도 엑셀 영양제(supplementId)도 아닌 것
  const isManual = item.catalogProductId == null && item.supplementId == null;
  return {
    id: item.userProductId,
    name: item.productName,
    brand: item.manufacturer ?? "",
    ingredient: item.ingredientSummary ?? "-",
    amount: item.packageSize ?? "-",
    price: item.price == null ? "-" : `${item.price.toLocaleString("ko-KR")}원`,
    category: item.category ?? (isManual ? "직접 입력" : "영양제"),
    color: cardColors[index % cardColors.length],
    time: scheduleText(item.schedules ?? []),
    // 복용 일정 수정 페이지에 넘길 원래 응답
    raw: item,
  };
}

function SettingsModal({ onClose, openerRef, active = true, children }) {
  const panelRef = useModalFocus(onClose, openerRef, {active});

  return (
    <div className="settings-overlay" role="presentation" onMouseDown={onClose}>
      <section
        ref={panelRef}
        className="settings-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="settings-header">
          <h2 id="settings-title">설정</h2>
          <button type="button" aria-label="설정 닫기" onClick={onClose}>
            ✕
          </button>
        </div>

        {children}
      </section>
    </div>
  );
}

export default function ProfileScreen({
  userId,
  friend,
  onUserUpdated,
  onBack,
  onNotifications,
  topBarActions,
  startAdding = false, // true면 영양제 등록 화면으로 바로 시작 (홈의 "영양제 등록하기")
  onRegisterBack,
  onDismissPanel,
  restoreChild = null,
  account = null, // 로그인 계정 { email, nickname } (설정의 계정 정보)
  onLogout, // 설정의 "로그아웃"
  onWithdraw,
  loggingOut = false,
}) {
  const isMine = !friend;
  const [user, setUser] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  // 친구가 복용 현황을 비공개로 두면 통계·달력·영양제 대신 안내만 보여줌
  const hidden = !isMine && friend.shared === false;
  const [editing, setEditing] = useState(false);
  const [stats, setStats] = useState(null);
  // 내 영양제 카드 목록, null 이면 아직 불러오는 중
  const [mySupplementList, setMySupplementList] = useState(null);
  const [supplementError, setSupplementError] = useState(false);
  // 등록·삭제 후 숫자를 올려서 통계·영양관리·영양제 목록을 다시 불러옴
  const [refreshKey, setRefreshKey] = useState(0);
  const [adding, setAdding] = useState(startAdding);
  // 복용 일정을 수정할 내 영양제 (API 응답 1개), null 이면 수정 안 함
  const [scheduleTarget, setScheduleTarget] = useState(null);
  const [supplementPanelInstanceId, setSupplementPanelInstanceId] = useState(
    () => ++nextSupplementPanelInstanceId,
  );
  const supplementPanelInstanceRef = useRef(supplementPanelInstanceId);
  const renewSupplementPanelInstance = useCallback(() => {
    const next = ++nextSupplementPanelInstanceId;
    supplementPanelInstanceRef.current = next;
    setSupplementPanelInstanceId(next);
  }, []);
  // 삭제 중인 카드 id (중복 클릭 방지)
  const [removingId, setRemovingId] = useState(null);
  const [sharing, setSharing] = useState(false);
  // 공개 설정 요청 중인지 (설정 창의 Esc 처리는 처음 함수를 계속 써서 state 대신 ref로 확인)
  const sharingRef = useRef(false);
+  const [withdrawConfirmOpen, setWithdrawConfirmOpen] = useState(false);  // 회원 탈퇴 최종 확인 창이 열려 있는지 (열려 있으면 설정 창을 닫지 않음)
  // 삭제 확인 창을 띄운 영양제 카드, null 이면 창 닫힘
  const [removeTarget, setRemoveTarget] = useState(null);
  // 삭제 확인 창을 연 버튼 (닫으면 포커스 복원)
  const removeOpenerRef = useRef(null);
  // 삭제 처리 중인지 (확인 창의 Esc 처리는 처음 함수를 계속 써서 state 대신 ref로 확인)
  const removingRef = useRef(false);
  // 브라우저 alert 대신 화면에 보여주는 실패 안내
  const [removeError, setRemoveError] = useState(null);
  const [shareError, setShareError] = useState(null);
  const shared = user?.shareIntakeStatus ?? false;
  const settingsOpenerRef = useRef(null);
  // 등록·수정 완료 토스트 { type, text }, null 이면 안 보임
  const [toast, setToast] = useState(null);

  // 영양제 등록·일정 수정 모달을 연 버튼 (닫으면 포커스 복원)
  const supplementOpenerRef = useRef(null);
  // 마이페이지의 "영양제 등록" 버튼 (홈에서 바로 연 모달을 닫으면 여기로 포커스 복원)
  const addButtonRef = useRef(null);

  useEffect(() => {
    if (!isMine || !restoreChild) return;
    if (restoreChild === "profile-edit") setEditing(true);
    else if (restoreChild === "supplement-add") {
      renewSupplementPanelInstance();
      setAdding(true);
    } else if (restoreChild === "settings") setShowSettings(true);
  }, [isMine, restoreChild, renewSupplementPanelInstance]);

  const restoredScheduleRef = useRef(null);
  useEffect(() => {
    if (!restoreChild?.startsWith("schedule:")) {
      restoredScheduleRef.current = null;
      return;
    }
    if (
      !isMine ||
      !mySupplementList ||
      restoredScheduleRef.current === restoreChild
    )
      return;
    const id = restoreChild.slice("schedule:".length);
    const target = mySupplementList.find((item) => String(item.id) === id)?.raw;
    if (target) {
      restoredScheduleRef.current = restoreChild;
      renewSupplementPanelInstance();
      setScheduleTarget(target);
    }
  }, [isMine, restoreChild, mySupplementList, renewSupplementPanelInstance]);

  useEffect(() => {
    if (!isMine || !onRegisterBack) return undefined;
    const close = editing
      ? () => setEditing(false)
      : adding
        ? () => setAdding(false)
        : scheduleTarget
          ? () => setScheduleTarget(null)
          : showSettings
            ? () => setShowSettings(false)
            : null;
    const childId = editing
      ? "profile-edit"
      : adding
        ? "supplement-add"
        : scheduleTarget
          ? `schedule:${scheduleTarget.userProductId}`
          : showSettings
            ? "settings"
            : null;
    onRegisterBack(
      "my",
      close,
      childId,
      adding || scheduleTarget ? supplementPanelInstanceId : null,
    );
    return () => onRegisterBack("my", null);
  }, [
    isMine,
    editing,
    adding,
    scheduleTarget,
    showSettings,
    supplementPanelInstanceId,
    onRegisterBack,
  ]);

  const dismissPanel = (
    close,
    expectedChildId = null,
    expectedInstanceId = null,
  ) => {
    if (
      expectedInstanceId != null &&
      supplementPanelInstanceRef.current !== expectedInstanceId
    ) {
      return false;
    }
    if (onDismissPanel?.("my", expectedChildId, expectedInstanceId) === false)
      return false;
    if (expectedInstanceId != null) supplementPanelInstanceRef.current = null;
    close();
    return true;
  };

  const openSettings = () => {
    settingsOpenerRef.current = document.activeElement;
    setShowSettings(true);
  };

  const toggleShare = async () => {
    if (sharing || !user) return;
    sharingRef.current = true;
    setSharing(true);
    setShareError(null);
    try {
      const updated = await updateShareIntake(!shared);
      setUser((prev) => ({
        ...prev,
        shareIntakeStatus: updated.shareIntakeStatus,
      }));
    } catch (e) {
      setShareError(e.message || "공개 설정을 바꾸지 못했어요.");
    } finally {
      sharingRef.current = false;
      setSharing(false);
    }
  };

  useEffect(() => {
    if (!isMine || userId == null) return;
    // 다시 불러오기(refreshKey 변경)로 이 effect가 다시 실행되면 true가 된다
    // → 늦게 도착한 이전 요청의 응답은 화면에 반영하지 않는다
    let ignore = false;

    fetchUser()
      .then((loadedUser) => {
        if (ignore) return;
        setUser(loadedUser);
      })
      .catch(() => {
        if (ignore) return;
        setUser(null);
      });
    fetchIntakeStats()
      .then((loadedStats) => {
        if (ignore) return;
        setStats(loadedStats);
      })
      .catch(() => {
        if (ignore) return;
        setStats(null);
      });
    fetchMySupplements()
      .then((list) => {
        if (ignore) return;
        setMySupplementList(list.map(toSupplementCard));
        setSupplementError(false);
      })
      .catch(() => {
        if (ignore) return;
        setSupplementError(true);
      });

    // 정리 함수: 다음 effect가 실행되기 전(또는 화면을 떠날 때) 호출된다
    return () => {
      ignore = true;
    };
  }, [isMine, userId, refreshKey]);

  // 홈의 "영양제 등록하기"로 바로 연 경우: openAdd를 거치지 않아서 마이페이지 등록 버튼을 복원 대상으로 지정
  useEffect(() => {
    if (startAdding) {
      supplementOpenerRef.current = addButtonRef.current;
    }
  }, []);

  // 토스트는 4초 뒤 자동으로 닫음 (홈 토스트와 같은 시간)
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  const reload = () => setRefreshKey((prev) => prev + 1);

  // 내 영양제는 로그인 사용자 데이터 사용
  // 친구 영양제는 API 응답을 화면 카드 형태로 변환
  const supplements = isMine
    ? (mySupplementList ?? [])
    : (friend?.supplements ?? []).map(toSupplementCard);

  // 사용자 이름
  const name = friend?.name ?? user?.nickname ?? "사용자";

  const initial = friend?.initial ?? name.charAt(0);

  if (isMine && editing) {
    return (
      <ProfileEditPage
        userId={userId}
        onBack={() => dismissPanel(() => setEditing(false))}
        onSaved={(updatedUser) => {
          setUser(updatedUser);
          onUserUpdated?.(updatedUser);
        }}
        onNotifications={onNotifications}
      />
    );
  }

  const openEdit = () => {
    setEditing(true);
    window.scrollTo({ top: 0 });
  };

  const openAdd = () => {
    supplementOpenerRef.current = document.activeElement;
    renewSupplementPanelInstance();
    setAdding(true);
  };

  // 삭제 버튼: 바로 지우지 않고 확인 창을 엶
  const askRemoveSupplement = (supplement) => {
    removeOpenerRef.current = document.activeElement;
    setRemoveError(null);
    setRemoveTarget(supplement);
  };

  // 확인 창의 [삭제]: 서버에서 복용 중단 처리 → 화면에서 바로 빼기 → 나머지(통계·영양관리) 다시 불러오기
  const removeSupplement = async () => {
    const supplement = removeTarget;
    removingRef.current = true;
    setRemovingId(supplement.id);
    const request = removeMySupplement(supplement.id);
    try {
      await withTimeout(
        request,
        REMOVE_TIMEOUT_MS,
        "응답이 늦어 삭제를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
      );
      // 재조회가 실패해도 삭제한 카드가 남지 않도록 목록에서 먼저 뺀다
      setMySupplementList((prev) => {
        const next = [];
        for (const item of prev ?? []) {
          if (item.id !== supplement.id) next.push(item);
        }
        return next;
      });
      removeOpenerRef.current = addButtonRef.current;
      setRemoveTarget(null);
      reload();
    } catch (e) {
      setRemoveTarget(null);
      setRemoveError(e.message || "삭제하지 못했어요.");
      request.then(
        () => {
          setRemoveError(null);
          reload();
        },
        () => {},
      );
    } finally {
      removingRef.current = false;
      setRemovingId(null);
    }
  };

  return (
    <div className={`screen profile-screen${isMine ? " is-mine" : ""}`}>
      {/* 머리: 큰 아바타 + 이름(화면 제목) + 오른쪽 버튼 (내 화면: 프로필 편집 / 친구: 안부지킴이·삭제) */}
      <TopBar
        back={onBack}
        avatar={
          <span
            className={`avatar xlarge${isMine ? "" : ` is-${friend.color ?? "mint"}${(friend.streak ?? 0) > 0 ? " is-streaking" : ""}`}`}
          >
            {initial}
          </span>
        }
        title={
          <>
            {name}
            {!isMine && friend?.relation && (
              <span
                className={`chip ${friend.mutual === false ? "" : "green"} profile-relation`}
              >
                {friend.relation}
              </span>
            )}
          </>
        }
        subtitle={isMine ? "건강한 하루를 만드는 중" : undefined}
        description={isMine ? undefined : friend?.status}
        onNotifications={onNotifications}
        onSettings={isMine ? openSettings : undefined}
        actions={
          isMine ? (
            <button
              className="btn btn-outline"
              type="button"
              onClick={openEdit}
            >
              프로필 편집
            </button>
          ) : (
            topBarActions
          )
        }
      />

      {showSettings && (
        <SettingsModal
          onClose={() => {
            if (!sharing)
              setShowSettings(false);
          }}
          openerRef={settingsOpenerRef}
          active={!withdrawConfirmOpen}
        >
          <section className="share-setting">
            <div>
              <strong>친구에게 복용 현황 공개</strong>
              <small>
                {shared
                  ? "나를 팔로우한 친구가 내 연속 복용, 영양제, 달력을 볼 수 있어요."
                  : "비공개예요. 친구는 내 복용 현황을 볼 수 없어요."}
              </small>
              {shareError && (
                <small className="share-error" role="alert">
                  {shareError}
                </small>
              )}
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={shared}
              aria-label="친구에게 복용 현황 공개"
              className={`switch ${shared ? "on" : ""}`}
              disabled={sharing || !user}
              onClick={toggleShare}
            >
              <span />
            </button>
          </section>
          {onLogout && (
            <section className="account-setting">
              <div>
                <strong>계정</strong>
                <small>
                  {account?.email ?? user?.email ?? "로그인한 계정"}
                </small>
              </div>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                disabled={loggingOut}
                onClick={onLogout}
              >
                {loggingOut ? "로그아웃 중..." : "로그아웃"}
              </button>
            </section>
          )}
          {isMine && onWithdraw && (
            <WithdrawalSection
              onWithdraw={onWithdraw}
              busy={loggingOut}
              onConfirmOpenChange={setWithdrawConfirmOpen}
            />
          )}
        </SettingsModal>
      )}
      {/* 영양제 등록 모달 */}
      {isMine && adding && (
        <SupplementAddPage
          userId={userId}
          openerRef={supplementOpenerRef}
          panelInstanceId={supplementPanelInstanceId}
          onBack={(instanceId) =>
            dismissPanel(() => setAdding(false), "supplement-add", instanceId)
          }
          onSaved={(instanceId, name) => {
            dismissPanel(() => setAdding(false), "supplement-add", instanceId);
            setToast({
              type: "success",
              text: `${name}을(를) 내 영양제에 등록했어요`,
            });
            reload();
          }}
        />
      )}
      {/* 복용 일정 수정 모달 (같은 컴포넌트, 제품은 고정) */}
      {isMine && scheduleTarget && (
        <SupplementAddPage
          userId={userId}
          target={scheduleTarget}
          openerRef={supplementOpenerRef}
          panelInstanceId={supplementPanelInstanceId}
          onBack={(instanceId) =>
            dismissPanel(
              () => setScheduleTarget(null),
              `schedule:${scheduleTarget.userProductId}`,
              instanceId,
            )
          }
          onSaved={(instanceId, name) => {
            dismissPanel(
              () => setScheduleTarget(null),
              `schedule:${scheduleTarget.userProductId}`,
              instanceId,
            );
            setToast({
              type: "success",
              text: `${name} 복용 일정을 수정했어요`,
            });
            reload();
          }}
        />
      )}

      {/* 영양제 삭제 확인 창 (브라우저 confirm 대신 화면 디자인에 맞춘 모달) */}
      {isMine && removeTarget && (
        <ConfirmDialog
          title="영양제 삭제"
          message={`'${removeTarget.name}'을(를) 내 영양제에서 삭제할까요?`}
          confirmLabel="삭제"
          danger
          busy={removingId === removeTarget.id}
          openerRef={removeOpenerRef}
          onConfirm={removeSupplement}
          onCancel={() => {
            // 삭제 처리 중에는 바깥을 누르거나 Esc를 눌러도 닫지 않음
            if (!removingRef.current) setRemoveTarget(null);
          }}
        />
      )}

      {/* 내 화면: 그래프 통계 카드 3개 / 친구 화면: 같은 그래프 (비공개면 안내) */}
      {hidden ? (
        <p className="share-hidden">
          {name}님이 복용 현황을 공개하지 않았어요.
        </p>
      ) : isMine ? (
        <MyStats stats={stats} />
      ) : (
        // 친구 상세: 친구 상세 API의 통계 값을 마이페이지 통계와 같은 모양으로 넘김
        <MyStats
          stats={{
            streakDays: friend.streak ?? 0,
            bestStreakDays: friend.bestStreakDays ?? 0,
            weeklyRate: friend.weeklyRate,
            weekDays: friend.weekDays,
            monthlyRate: friend.monthlyRate,
            monthWeeks: friend.monthWeeks,
          }}
        />
      )}
      {!hidden &&
        (isMine ? (
          <IntakeCalendar
            fetchProgress={getMyCalendarProgress}
            fetchDay={getMyDayIntakes}
            fetchEvents={getCalendarEvents}
            createEvent={createCalendarEvent}
            createPrescription={createPrescription}
            updatePrescription={updatePrescription}
            deletePrescription={deletePrescription}
            updateEvent={updateCalendarEvent}
            updateEventStatus={updateCalendarEventStatus}
            deleteEvent={deleteCalendarEvent}
            fetchInteractions={fetchInteractions}
          />
        ) : (
          <IntakeCalendar
            key={friend.userId}
            title={`${name}님의 복용 달력`}
            fetchDates={(s, e) => getFriendCalendar(friend.userId, s, e)}
            fetchDay={(d) => getFriendDayIntakes(friend.userId, d)}
          />
        ))}

      {/* 내 화면: 나의 영양관리(왼쪽) + 내가 먹는 영양제(오른쪽) / 친구 화면: 영양제만 */}
      <div className={isMine ? "profile-columns" : "profile-columns single"}>
        {/* key 가 바뀌면 다시 그려지면서 영양관리도 다시 불러옴 */}
        {isMine && <NutritionManagement key={refreshKey} userId={userId} />}

        <section className="products-section">
          <div className="products-head">
            <h2>
              {isMine ? "내가 먹는 영양제" : `${name}님이 먹는 영양제`}
              <span>{supplements.length}개</span>
            </h2>
            {isMine && (
              <button
                ref={addButtonRef}
                className="btn btn-soft btn-sm"
                type="button"
                onClick={openAdd}
              >
                <Icon name="plus" size={15} />
                영양제 등록
              </button>
            )}
          </div>
          {isMine && removeError && (
            <p
              className="section-description supplement-action-error"
              role="alert"
            >
              {removeError}
            </p>
          )}
          {isMine && supplementError && (
            <p className="section-description">
              영양제 정보를 불러오지 못했어요.
            </p>
          )}
          {isMine && !supplementError && mySupplementList?.length === 0 && (
            <p className="section-description">아직 등록된 영양제가 없어요.</p>
          )}
          {!isMine && hidden && (
            <p className="section-description">
              {name}님이 복용 현황을 공개하지 않았어요.
            </p>
          )}
          {!isMine && !hidden && supplements.length === 0 && (
            <p className="section-description">아직 등록된 영양제가 없어요.</p>
          )}
          {/* 아침 · 점심 · 저녁 시간대별 묶음 (#67 영양제 시안 B) */}
          <SupplementGroups
            supplements={supplements}
            isMine={isMine}
            removingId={removingId}
            onEdit={(supplement) => {
              supplementOpenerRef.current = document.activeElement;
              renewSupplementPanelInstance();
              setScheduleTarget(supplement.raw);
            }}
            onRemove={askRemoveSupplement}
          />
        </section>
      </div>

      {/* 최근 추천 제품 보기 (V7, 담당: 영준). 내 화면에서만 */}
      {isMine && <RecentRecommendationSection />}

      {/* 등록·수정 완료 안내 (홈 복용 체크 토스트와 같은 모양) */}
      <HomeToast toast={toast} onClose={() => setToast(null)} />
    </div>
  );
}

// 게이지 상태: 상한 초과 → 과다, 권장량 절반 미만 → 부족, 권장량 이상 → 충분(상한 미평가 성분은 상한 미평가), 그 외 → 퍼센트
function nutrientStatus(item) {
  if (item.overUpper) {
    return { className: "over", label: "과다" };
  }
  if (item.percent < 50) {
    return { className: "low", label: "부족" };
  }
  // 비타민 A처럼 상한을 평가하지 못한 성분은 권장량을 넘어도 "충분" 대신 "상한 미평가"
  if (item.percent >= 100 && item.upperUnchecked) {
    return { className: "unchecked", label: "상한 미평가" };
  }
  if (item.percent >= 100) {
    return { className: "ok", label: "충분" };
  }
  return { className: "", label: `${item.percent}%` };
}

// ───────────── 나의 영양관리 ─────────────

// 권장량에 가장 못 미치는 성분 안내
function nutritionNoticeText(nutrients) {
  if (nutrients.length === 0) {
    return "영양 정보를 불러오지 못했어요.";
  }
  // 상한을 넘은 성분이 있으면 부족보다 먼저 알림
  const overNames = [];
  for (const item of nutrients) {
    if (item.overUpper) overNames.push(item.name);
  }
  if (overNames.length > 0) {
    return `${overNames.join(", ")}이(가) 하루 상한 섭취량을 넘었어요. 과다 섭취는 건강에 해로울 수 있으니 약사나 의사와 상담해 보세요.`;
  }
  let lowest = nutrients[0];
  for (const item of nutrients) {
    if (item.percent < lowest.percent) lowest = item;
  }
  const message =
    lowest.percent < 100
      ? `${lowest.name} 섭취가 권장량보다 부족해요.`
      : "모든 성분이 권장량을 채웠어요.";
  return `${message} 식품 섭취량은 포함되지 않은 참고용 수치예요.`;
}

function NutritionManagement({ userId }) {
  const [nutrition, setNutrition] = useState(null);

  // 펼쳐서 상세를 보고 있는 성분 이름, null 이면 모두 접힘
  const [openName, setOpenName] = useState(null);

  // 같은 성분을 다시 누르면 접고, 다른 성분을 누르면 그 성분을 펼침
  const toggleDetail = (name) => {
    if (openName === name) {
      setOpenName(null);
    } else {
      setOpenName(name);
    }
  };

  useEffect(() => {
    if (userId == null) return;

    let ignore = false;

    fetchNutrition()
      .then((data) => {
        if (!ignore) setNutrition(data);
      })
      .catch(() => {
        if (!ignore) setNutrition(null);
      });

    return () => {
      ignore = true;
    };
  }, [userId]);

  const nutrients = nutrition?.nutrients ?? [];
  // 복용 중이지만 함량 정보가 없어 계산에서 빠진 영양제 수
  const unsupportedCount = nutrition?.unsupportedCount ?? 0;

  // 서버가 먹는 성분만 보내므로, 목록이 비어 있으면 계산할 영양제가 없는 것 → 게이지 대신 안내
  const hasIntake = nutrients.length > 0;

  return (
    <section className="nutrition-management">
      <div className="nutrition-heading">
        <div>
          <h2>나의 영양관리</h2>
          <p>
            등록한 영양제를 하루 권장 섭취량대로 먹었을 때의 양을, 설문 정보로
            정한 나의 하루 권장량(2025 한국인 영양소 섭취기준)과 비교했어요.
            설문 정보가 없으면 19~29세 남성 기준을 사용해요.
          </p>
        </div>
        <span className="nutrition-date">
          {nutrition?.sample ? "오늘 기준" : "오늘 기준"}
        </span>
      </div>

      {nutrition && !hasIntake ? (
        <p className="nutrition-empty">
          {unsupportedCount > 0
            ? "등록한 영양제의 성분 함량 정보가 아직 없어서 계산하지 못했어요."
            : "영양제를 등록하면 성분별 하루 섭취량을 권장량과 비교해 보여드려요."}
        </p>
      ) : (
        <>
          <div className="nutrition-list">
            {nutrients.map((item) => {
              const status = nutrientStatus(item);
              const open = openName === item.name;
              const sources = item.sources ?? [];
              return (
                <div className="nutrition-item" key={item.name}>
                  <div className="nutrition-row">
                    {/* 이름을 누르면 섭취량 전체와 영양제별 출처를 펼침 */}
                    <button
                      type="button"
                      className="nutrition-name"
                      aria-expanded={open}
                      onClick={() => toggleDetail(item.name)}
                    >
                      <strong>{item.name}</strong>
                      <span>
                        {item.intakeAmount}
                        {item.unit} / 권장 {item.recommendedAmount}
                        {item.unit}
                      </span>
                      {item.overUpper && (
                        <span className="nutrition-upper">
                          상한 {item.upperAmount}
                          {item.unit}
                        </span>
                      )}
                    </button>
                    <div className="nutrition-gauge">
                      <span
                        className={status.className}
                        style={{ width: `${Math.min(item.percent, 100)}%` }}
                      />
                    </div>
                    <div className={`nutrition-percent ${status.className}`}>
                      {status.label}
                    </div>
                  </div>

                  {open && (
                    <div className="nutrition-detail">
                      <p>
                        하루 {item.intakeAmount}
                        {item.unit} · 권장 {item.recommendedAmount}
                        {item.unit}
                        {item.upperAmount > 0 &&
                          ` · 상한 ${item.upperAmount}${item.unit}`}
                      </p>
                      {item.upperUnchecked && (
                        <p className="nutrition-detail-note">
                          제품 성분표에 레티놀과 베타카로틴이 합쳐 적혀 있어
                          원료별 함량을 구분할 수 없어요. 그래서 상한
                          섭취량(레티놀 기준 3,000㎍)은 평가하지 않았어요.
                        </p>
                      )}
                      {sources.length >= 2 && (
                        <p className="nutrition-detail-dup">
                          {sources.length}개 제품에 들어 있어요
                        </p>
                      )}
                      <ul>
                        {sources.map((source) => (
                          <li key={source.userProductId}>
                            <span>{source.productName}</span>
                            <strong>
                              {source.amount}
                              {item.unit}
                            </strong>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          <div className="nutrition-notice">
            <Icon name="sparkles" size={17} />
            <p>{nutritionNoticeText(nutrients)}</p>
          </div>
          {unsupportedCount > 0 && (
            <p className="nutrition-empty">
              함량 정보가 없는 영양제 {unsupportedCount}개는 계산에서 빠졌어요.
            </p>
          )}
        </>
      )}
    </section>
  );
}
