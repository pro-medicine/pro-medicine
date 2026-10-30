import { useRef, useEffect, useState } from "react";
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
  updateCalendarEvent,
  deleteCalendarEvent,
  updateCalendarEventStatus,
} from "../api/calendarEventApi";
import { useModalFocus } from "../common/useModalFocus";
import ProfileEditPage from "./ProfileEditPage";
import SupplementAddPage from "./SupplementAddPage";
import IntakeCalendar from "../common/IntakeCalendar";
import { getMyCalendarProgress, getMyDayIntakes } from "../api/intakeApi";
import MyStats from "./MyStats";
import SupplementGroups from "./SupplementGroups";
import { getFriendCalendar, getFriendDayIntakes } from "../api/friendApi";

// 카드 병 색상: shared/ui 예시 데이터에서 쓰는 색상 이름을 순서대로 돌려 씀
const cardColors = ["sun", "ocean", "lilac"];

// 끼니 / 식전·식후 코드 → 화면 문구 (ANY = 식사와 상관없음 → 끼니만 표시)
const mealSlotLabels = { BREAKFAST: "아침", LUNCH: "점심", DINNER: "저녁" };
const mealRelationLabels = { BEFORE: "식전", AFTER: "식후", ANY: "" };

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
  const isManual = item.catalogProductId === null;
  return {
    id: item.userProductId,
    name: item.productName,
    brand: item.manufacturer ?? "",
    ingredient: item.ingredientSummary ?? "-",
    amount: item.packageSize ?? "-",
    price:
      item.price == null ? "-" : `${item.price.toLocaleString("ko-KR")}원`,
    category: item.category ?? (isManual ? "직접 입력" : "영양제"),
    color: cardColors[index % cardColors.length],
    time: scheduleText(item.schedules ?? []),
    // 복용 일정 수정 페이지에 넘길 원래 응답
    raw: item,
  };
}

function SettingsModal({ onClose, openerRef, children }) {
  const panelRef = useModalFocus(onClose, openerRef);

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
  viewerId,
  onUserUpdated,
  onBack,
  onNotifications,
  topBarActions,
  startAdding = false, // true면 영양제 등록 화면으로 바로 시작 (홈의 "영양제 등록하기")
  account = null, // 로그인 계정 { email, nickname } (설정의 계정 정보)
  onLogout, // 설정의 "로그아웃"
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
  // 삭제 중인 카드 id (중복 클릭 방지)
  const [removingId, setRemovingId] = useState(null);
  const [sharing, setSharing] = useState(false);
  const shared = user?.shareIntakeStatus ?? false;
  const settingsOpenerRef = useRef(null);

  const openSettings = () => {
    settingsOpenerRef.current = document.activeElement;
    setShowSettings(true);
  };

  const toggleShare = async () => {
    if (sharing || !user) return;
    setSharing(true);
    try {
      const updated = await updateShareIntake(userId, !shared);
      setUser((prev) => ({
        ...prev,
        shareIntakeStatus: updated.shareIntakeStatus,
      }));
    } catch (e) {
      window.alert(e.message || "공개 설정을 바꾸지 못했어요.");
    } finally {
      setSharing(false);
    }
  };

  useEffect(() => {
    if (!isMine || userId == null) return;
    // 다시 불러오기(refreshKey 변경)로 이 effect가 다시 실행되면 true가 된다
    // → 늦게 도착한 이전 요청의 응답은 화면에 반영하지 않는다
    let ignore = false;

    fetchUser(userId)
      .then((loadedUser) => {
        if (ignore) return;
        setUser(loadedUser);
      })
      .catch(() => {
        if (ignore) return;
        setUser(null);
      });
    fetchIntakeStats(userId)
      .then((loadedStats) => {
        if (ignore) return;
        setStats(loadedStats);
      })
      .catch(() => {
        if (ignore) return;
        setStats(null);
      });
    fetchMySupplements(userId)
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
            onBack={() => setEditing(false)}
            onSaved={(updatedUser) => {
              setUser(updatedUser);
              onUserUpdated?.(updatedUser);
            }}
            onNotifications={onNotifications}
        />
    );
  }

  // 영양제 등록 페이지: 등록에 성공하면 마이페이지로 돌아와서 목록·통계·영양관리를 다시 불러옴
  if (isMine && adding) {
    return (
        <SupplementAddPage
            userId={userId}
            onBack={() => setAdding(false)}
            onSaved={() => {
              setAdding(false);
              reload();
            }}
            onNotifications={onNotifications}
        />
    );
  }

  // 같은 페이지를 수정 모드로 사용 (제품은 고정, 복용 일정만 수정)
  if (isMine && scheduleTarget) {
    return (
        <SupplementAddPage
            userId={userId}
            target={scheduleTarget}
            onBack={() => setScheduleTarget(null)}
            onSaved={() => {
              setScheduleTarget(null);
              reload();
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
    setAdding(true);
    window.scrollTo({ top: 0 });
  };

  // 삭제: 확인 → 서버에서 복용 중단 처리 → 화면에서 바로 빼기 → 나머지(통계·영양관리) 다시 불러오기
  const removeSupplement = async (supplement) => {
    if (!window.confirm(`'${supplement.name}'을(를) 내 영양제에서 삭제할까요?`))
      return;
    setRemovingId(supplement.id);
    try {
      await removeMySupplement(userId, supplement.id);
      // 재조회가 실패해도 삭제한 카드가 남지 않도록 목록에서 먼저 뺀다
      setMySupplementList((prev) => {
        const next = [];
        for (const item of prev ?? []) {
          if (item.id !== supplement.id) next.push(item);
        }
        return next;
      });
      reload();
    } catch (e) {
      window.alert(e.message || "삭제하지 못했어요.");
    } finally {
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
              <span className={`chip ${friend.mutual === false ? "" : "green"} profile-relation`}>
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
            <button className="btn btn-outline" type="button" onClick={openEdit}>
              프로필 편집
            </button>
          ) : (
            topBarActions
          )
        }
      />

      {showSettings && (
        <SettingsModal onClose={() => setShowSettings(false)} openerRef={settingsOpenerRef}>
          <section className="share-setting">
            <div>
              <strong>친구에게 복용 현황 공개</strong>
              <small>
                {shared
                  ? "나를 팔로우한 친구가 내 연속 복용, 영양제, 달력을 볼 수 있어요."
                  : "비공개예요. 친구는 내 복용 현황을 볼 수 없어요."}
              </small>
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
                <small>{account?.email ?? user?.email ?? "로그인한 계정"}</small>
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
        </SettingsModal>
      )}

      {/* 내 화면: 그래프 통계 카드 3개 / 친구 화면: 같은 그래프 (비공개면 안내) */}
      {hidden ? (
        <p className="share-hidden">{name}님이 복용 현황을 공개하지 않았어요.</p>
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
            updateEvent={updateCalendarEvent}
            updateEventStatus={updateCalendarEventStatus}
            deleteEvent={deleteCalendarEvent}
          />
        ) : (
          <IntakeCalendar
            key={friend.userId}
            title={`${name}님의 복용 달력`}
            fetchDates={(s, e) =>
              getFriendCalendar(viewerId, friend.userId, s, e)
            }
            fetchDay={(d) => getFriendDayIntakes(viewerId, friend.userId, d)}
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
              <button className="btn btn-soft btn-sm" type="button" onClick={openAdd}>
                <Icon name="plus" size={15} />
                영양제 등록
              </button>
            )}
          </div>
          {isMine && supplementError && (
            <p className="section-description">
              영양제 정보를 불러오지 못했어요.
            </p>
          )}
          {isMine && !supplementError && mySupplementList?.length === 0 && (
            <p className="section-description">아직 등록된 영양제가 없어요.</p>
          )}
          {!isMine && hidden && (
            <p className="section-description">{name}님이 복용 현황을 공개하지 않았어요.</p>
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
              setScheduleTarget(supplement.raw);
              window.scrollTo({ top: 0 });
            }}
            onRemove={removeSupplement}
          />
        </section>
      </div>
    </div>
  );
}

// ───────────── 나의 영양관리 ─────────────

// 권장량에 가장 못 미치는 성분 안내
function nutritionNoticeText(nutrients) {
  if (nutrients.length === 0) {
    return "영양 정보를 불러오지 못했어요.";
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

function NutritionManagement({userId}) {
  const [nutrition, setNutrition] = useState(null);

  useEffect(() => {
    if (userId == null) return;

    let ignore = false;

    fetchNutrition(userId)
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

  // 섭취량이 모두 0이면 계산할 영양제가 없는 것 → 게이지 대신 안내
  let hasIntake = false;
  for (const item of nutrients) {
    if (item.intakeAmount > 0) hasIntake = true;
  }

  return (
    <section className="nutrition-management">
      <div className="nutrition-heading">
        <div>
          <h2>나의 영양관리</h2>
          <p>복용 중인 영양제를 기준으로 성인 하루 권장량과 비교했어요.</p>
        </div>
        <span className="nutrition-date">
          {nutrition?.sample ? "오늘 기준 · 예시 데이터" : "오늘 기준"}
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
            {nutrients.map((item) => (
              <div className="nutrition-row" key={item.name}>
                <div className="nutrition-name">
                  <strong>{item.name}</strong>
                  <span>
                    {item.intakeAmount}
                    {item.unit} / 권장 {item.recommendedAmount}
                    {item.unit}
                  </span>
                </div>
                <div className="nutrition-gauge">
                  <span
                    className={item.percent < 50 ? "low" : "ok"}
                    style={{ width: `${Math.min(item.percent, 100)}%` }}
                  />
                </div>
                <div className={`nutrition-percent ${item.percent < 50 ? "low" : item.percent >= 100 ? "ok" : ""}`}>
                  {item.percent < 50 ? "부족" : item.percent >= 100 ? "충분" : `${item.percent}%`}
                </div>
              </div>
            ))}
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
