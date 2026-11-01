// 먹는 영양제 목록: 아침 · 점심 · 저녁 시간대별로 묶어서 보여줌 (#67 영양제 시안 B)
// 마이페이지(내 영양제: 수정·삭제 버튼)와 친구 상세(보기만)에서 같이 사용
// supplements: MyPage의 toSupplementCard 결과 (raw = API 응답 원본)
import { Icon } from "../shared/ui";

// 시간대 아이콘: 아침 = 해 뜨는 모양, 점심 = 해, 저녁 = 달
const SLOT_PATHS = {
  morning: (
    <>
      <path d="M17 18a5 5 0 0 0-10 0" />
      <path d="M12 2v7M4.2 10.2l1.4 1.4M1 18h2M21 18h2M18.4 11.6l1.4-1.4M23 22H1M8 6l4-4 4 4" />
    </>
  ),
  lunch: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  evening: <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />,
};

const SLOTS = [
  { id: "BREAKFAST", label: "아침", tone: "morning" },
  { id: "LUNCH", label: "점심", tone: "lunch" },
  { id: "DINNER", label: "저녁", tone: "evening" },
];
const RELATION = { BEFORE: "식전", AFTER: "식후" };

// 종류 → 아이콘 모양·색 (정해진 종류가 아니면 기본 알약)
const ICONS = {
  pill: (
    <>
      <path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z" />
      <path d="m8.5 8.5 7 7" />
    </>
  ),
  fish: (
    <>
      <path d="M6.5 12c3-5 9-6 14-3-1 1-1 5 0 6-5 3-11 2-14-3Z" />
      <path d="M6.5 12 2 8.5v7Z" />
    </>
  ),
  drop: <path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
};

function categoryKind(category = "") {
  if (category === "직접 입력") return { icon: "pill", tone: "gray" };
  if (category.includes("오메가")) return { icon: "fish", tone: "blue" };
  if (/비타민\s?D/i.test(category)) return { icon: "sun", tone: "yellow" };
  if (/마그네슘|칼슘|아연|철분|미네랄|셀레늄/.test(category)) return { icon: "drop", tone: "purple" };
  return { icon: "pill", tone: "mint" };
}

function CategoryTile({ category }) {
  const { icon, tone } = categoryKind(category);
  return (
    <span className={`supp-tile is-${tone}`} aria-hidden="true">
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        {ICONS[icon]}
      </svg>
    </span>
  );
}

// 영양제 목록 → [{ slot, items: [{ supplement, schedule }] }] (하루 두 번이면 두 시간대에 모두 들어감)
function groupBySlot(supplements) {
  const groups = SLOTS.map((slot) => ({ ...slot, items: [] }));
  const none = { id: "NONE", label: "일정 없음", tone: "none", items: [] };
  for (const supplement of supplements) {
    const schedules = supplement.raw?.schedules ?? [];
    if (schedules.length === 0) {
      none.items.push({ supplement, schedule: null });
      continue;
    }
    for (const schedule of schedules) {
      const group = groups.find((g) => g.id === schedule.mealSlot);
      if (group) group.items.push({ supplement, schedule });
      else none.items.push({ supplement, schedule });
    }
  }
  for (const group of groups) {
    group.items.sort((a, b) => (a.schedule.remindAt ?? "").localeCompare(b.schedule.remindAt ?? ""));
  }
  return [...groups, none].filter((g) => g.items.length > 0);
}

function detailText(supplement, schedule) {
  return [
    schedule ? RELATION[schedule.mealRelation] : null,
    schedule?.remindAt ? schedule.remindAt.slice(0, 5) : null,
    supplement.brand || null,
    supplement.amount !== "-" ? supplement.amount : null,
    supplement.price !== "-" ? supplement.price : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

export default function SupplementGroups({ supplements, isMine, removingId, onEdit, onRemove }) {
  if (supplements.length === 0) return null;
  return (
    <div className="supp-groups">
      {groupBySlot(supplements).map((group) => (
        <section key={group.id} className="supp-group" aria-label={`${group.label} 영양제`}>
          <h3 className="supp-group-head">
            <span className={`supp-slot-mark is-${group.tone}`} aria-hidden="true">
              {SLOT_PATHS[group.tone] ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  {SLOT_PATHS[group.tone]}
                </svg>
              ) : (
                <Icon name="clock" size={15} />
              )}
            </span>
            {group.label}
            <small>{group.items.length}개</small>
          </h3>
          <ul>
            {group.items.map(({ supplement, schedule }) => (
              <li key={`${group.id}-${supplement.id}`} className="supp-row">
                <CategoryTile category={supplement.category} />
                <div className="supp-main">
                  <div className="supp-name">
                    <strong>{supplement.name}</strong>
                    <span className={`supp-chip is-${categoryKind(supplement.category).tone}`}>
                      {supplement.category}
                    </span>
                  </div>
                  <small>
                    {detailText(supplement, schedule) ||
                      (supplement.category === "직접 입력"
                        ? "직접 입력한 영양제"
                        : isMine
                          ? "복용 일정을 등록해 보세요"
                          : "복용 일정 없음")}
                  </small>
                </div>
                {isMine && (
                  <span className="supp-actions">
                    <button
                      type="button"
                      title="복용 일정 수정"
                      aria-label={`${supplement.name} 복용 일정 수정`}
                      onClick={() => onEdit(supplement)}
                    >
                      <Icon name="pencil" size={16} />
                    </button>
                    <button
                      type="button"
                      className="is-danger"
                      title="삭제"
                      aria-label={`${supplement.name} 삭제`}
                      disabled={removingId === supplement.id}
                      onClick={() => onRemove(supplement)}
                    >
                      <Icon name="trash" size={16} />
                    </button>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
