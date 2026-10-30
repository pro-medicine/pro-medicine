import { useRef, useEffect, useState } from "react";
import { Icon, SupplementCard, TopBar } from "../shared/ui";
import {
  fetchIntakeStats,
  fetchMySupplements,
  fetchNutrition,
  fetchUser,
  MY_USER_ID,
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
import ProfileEditPage from "./ProfileEditPage";
import SupplementAddPage from "./SupplementAddPage";
import IntakeCalendar from "../common/IntakeCalendar";
import { getMyCalendar, getMyDayIntakes } from "../api/intakeApi";
import { getFriendCalendar, getFriendDayIntakes } from "../api/friendApi";
import { useModalFocus } from "../common/useModalFocus";

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
      item.price === null ? "-" : `${item.price.toLocaleString("ko-KR")}원`,
    category: item.category ?? (isManual ? "직접 입력" : "영양제"),
    color: cardColors[index % cardColors.length],
    time: scheduleText(item.schedules ?? []),
    // 복용 일정 수정 페이지에 넘길 원래 응답
    raw: item,
  };
}

export default function ProfileScreen({
  friend,
  viewerId,
  onBack,
  onNotifications,
  topBarActions,
}) {
  const isMine = !friend;
  const [user, setUser] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const hidden = !isMine && friend.shared === false;
  const [editing, setEditing] = useState(false);
  const [stats, setStats] = useState(null);
  // 내 영양제 카드 목록, null 이면 아직 불러오는 중
  const [mySupplementList, setMySupplementList] = useState(null);
  const [supplementError, setSupplementError] = useState(false);
  // 등록·삭제 후 숫자를 올려서 통계·영양관리·영양제 목록을 다시 불러옴
  const [refreshKey, setRefreshKey] = useState(0);
  const [adding, setAdding] = useState(false);
  // 복용 일정을 수정할 내 영양제 (API 응답 1개), null 이면 수정 안 함
  const [scheduleTarget, setScheduleTarget] = useState(null);
  // 삭제 중인 카드 id (중복 클릭 방지)
  const [removingId, setRemovingId] = useState(null);
  const [sharing, setSharing] = useState(false);
  const shared = user?.shareIntakeStatus ?? false;
  const settingsOpenerRef = useRef(null);
  const settingsPanelRef = useModalFocus(
    () => setShowSettings(false),
    settingsOpenerRef,
  );

  const openSettings = () => {
    settingsOpenerRef.current = document.activeElement;
    setShowSettings(true);
  };

  const toggleShare = async () => {
    if (sharing || !user) return;
    setSharing(true);
    try {
      const updated = await updateShareIntake(MY_USER_ID, !shared);
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
    if (!isMine) return;
    // 다시 불러오기(refreshKey 변경)로 이 effect가 다시 실행되면 true가 된다
    // → 늦게 도착한 이전 요청의 응답은 화면에 반영하지 않는다
    let ignore = false;

    fetchUser(MY_USER_ID)
      .then((loadedUser) => {
        if (ignore) return;
        setUser(loadedUser);
      })
      .catch(() => {
        if (ignore) return;
        setUser(null);
      });
    fetchIntakeStats(MY_USER_ID)
      .then((loadedStats) => {
        if (ignore) return;
        setStats(loadedStats);
      })
      .catch(() => {
        if (ignore) return;
        setStats(null);
      });
    fetchMySupplements(MY_USER_ID)
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
  }, [isMine, refreshKey]);

  const reload = () => setRefreshKey((prev) => prev + 1);

  // 내 화면은 API 값, 친구 화면은 기존 예시 데이터
  const supplements = isMine
    ? (mySupplementList ?? [])
    : (friend.supplements ?? []).map(toSupplementCard); // 내 정보 API를 못 불러오면 기존 예시 이름을 보여줌
  const name = friend?.name ?? user?.nickname ?? "김민지";
  const initial = friend?.initial ?? name.charAt(0);
  const avatarColor = friend?.color ?? "yellow";
  // 내 통계는 API 값, 못 불러오면 "-" / 친구 화면은 기존 예시 데이터
  const streak = isMine ? (stats?.streakDays ?? "-") : (friend.streak ?? 0);
  const productCount = isMine
    ? (stats?.activeProductCount ?? "-")
    : (friend.supplementCount ?? supplements.length);
  const monthlyRate = isMine
    ? (stats?.monthlyRate ?? "-")
    : (friend.monthlyRate ?? "-");

  if (isMine && editing) {
    return (
      <ProfileEditPage
        onBack={() => setEditing(false)}
        onSaved={setUser}
        onNotifications={onNotifications}
      />
    );
  }

  // 영양제 등록 페이지: 등록에 성공하면 마이페이지로 돌아와서 목록·통계·영양관리를 다시 불러옴
  if (isMine && adding) {
    return (
      <SupplementAddPage
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
      await removeMySupplement(MY_USER_ID, supplement.id);
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
    <div className="screen profile-screen">
      <TopBar
        back={onBack}
        title={isMine ? "마이페이지" : `${name}님의 영양정보`}
        subtitle={isMine ? "나의 건강 기록" : "친구 프로필"}
        onNotifications={onNotifications}
        onSettings={isMine ? openSettings : undefined}
        actions={topBarActions}
      />
      {showSettings && (
        <div className="settings-overlay">
          <section
            ref={settingsPanelRef}
            className="settings-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="settings-title"
          >
            <div className="settings-header">
              <h2 id="settings-title">설정</h2>

              <button
                type="button"
                aria-label="설정 닫기"
                onClick={() => setShowSettings(false)}
              >
                ✕
              </button>
            </div>

            {isMine && (
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
            )}
          </section>
        </div>
      )}
      <section
        className={isMine ? "profile-hero clickable" : "profile-hero"}
        onClick={isMine ? openEdit : undefined}
      >
        <span className={`avatar xlarge ${avatarColor}`}>{initial}</span>
        <div className="profile-name">
          <span>{isMine ? "건강한 하루를 만드는 중" : friend?.relation}</span>
          <h2>{name}</h2>
          <p>{isMine ? "꾸준함이 최고의 영양제예요" : friend?.status}</p>
        </div>
        {isMine && (
          <button className="edit-button" type="button" onClick={openEdit}>
            프로필 편집
          </button>
        )}
      </section>
      {hidden ? (
        <p className="share-hidden">
          {name}님이 복용 현황을 공개하지 않았어요.
        </p>
      ) : (
        <section className="stats-grid">
          <div>
            <span>연속 복용</span>
            <strong>
              {streak}
              <small>일</small>
            </strong>
          </div>
          <div>
            <span>복용 영양제</span>
            <strong>
              {productCount}
              <small>개</small>
            </strong>
          </div>
          <div>
            <span>이번 달 달성률</span>
            <strong>
              {monthlyRate}
              <small>%</small>
            </strong>
          </div>
        </section>
      )}
      {!hidden &&
        (isMine ? (
          <IntakeCalendar
            fetchDates={getMyCalendar}
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
      {/* key 가 바뀌면 다시 그려지면서 영양관리도 다시 불러옴 */}
      {isMine && <NutritionManagement key={refreshKey} />}

      <section className="section-block products-section">
        <div className="section-title-row">
          <div>
            <span className="section-label">SUPPLEMENTS</span>
            <h2>{isMine ? "내가 먹는 영양제" : `${name}님이 먹는 영양제`}</h2>
          </div>
          {isMine && (
            <button
              className="add-friend-button"
              type="button"
              onClick={openAdd}
            >
              <Icon name="plus" size={16} />
              영양제 등록
            </button>
          )}
        </div>
        <p className="section-description">
          상품명과 주요 영양성분, 함량 및 구매 가격을 확인할 수 있어요.
        </p>
        {isMine && supplementError && (
          <p className="section-description">
            영양제 정보를 불러오지 못했어요.
          </p>
        )}
        {isMine && !supplementError && mySupplementList?.length === 0 && (
          <p className="section-description">아직 등록된 영양제가 없어요.</p>
        )}
        {hidden ? (
          <p className="share-hidden">
            {name}님이 복용 현황을 공개하지 않았어요.
          </p>
        ) : (
          <div className="product-grid">
            {supplements.map((supplement) =>
              isMine ? (
                // 공용 SupplementCard 는 그대로 두고, 바깥을 감싸서 삭제 버튼만 얹음
                <div className="supplement-card-wrap" key={supplement.id}>
                  <SupplementCard supplement={supplement} />
                  {/* 카드 오른쪽 위: 수정(복용 일정) / 삭제 */}
                  <div className="supplement-card-actions">
                    <button
                      type="button"
                      aria-label={`${supplement.name} 복용 일정 수정`}
                      onClick={() => {
                        setScheduleTarget(supplement.raw);
                        window.scrollTo({ top: 0 });
                      }}
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      aria-label={`${supplement.name} 삭제`}
                      disabled={removingId === supplement.id}
                      onClick={() => removeSupplement(supplement)}
                    >
                      삭제
                    </button>
                  </div>
                </div>
              ) : (
                <SupplementCard key={supplement.id} supplement={supplement} />
              ),
            )}
          </div>
        )}
      </section>
    </div>
  );
}

// ───────────── 나의 영양관리 ─────────────

// 성분 순서대로 게이지 색상
const nutritionTones = ["yellow", "green", "purple", "orange", "blue"];

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

function NutritionManagement() {
  const [nutrition, setNutrition] = useState(null);

  useEffect(() => {
    fetchNutrition(MY_USER_ID)
      .then(setNutrition)
      .catch(() => setNutrition(null));
  }, []);

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
          <span className="section-label">DAILY NUTRITION</span>
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
            {nutrients.map((item, index) => (
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
                    className={nutritionTones[index % nutritionTones.length]}
                    style={{ width: `${Math.min(item.percent, 100)}%` }}
                  />
                </div>
                <div className="nutrition-percent">
                  <strong>{item.percent}%</strong>
                  {item.percent > 100 && <span>충분</span>}
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
