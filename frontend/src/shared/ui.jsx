import { useContext } from "react";
import { UnreadNotificationContext } from "../common/unreadNotificationContext";

/**
 * SVG 아이콘
 * @param {{ name: "arrow"|"bell"|"check"|"chevron"|"clock"|"home"|"people"|"plus"|"pin"|"search"|"sparkles"|"trash"|"user", size?: number }} props
 */

export function Icon({ name, size = 20 }) {
  const paths = {
    arrow: <path d="m15 18-6-6 6-6" />,
    bell: (
      <>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    chevron: <path d="m9 18 6-6-6-6" />,
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    home: (
      <>
        <path d="m3 11 9-8 9 8" />
        <path d="M5 10v10h14V10M9 20v-6h6v6" />
      </>
    ),
    people: (
      <>
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    pin: (
      <>
        <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
        <circle cx="12" cy="10" r="2.5" />
      </>
    ),
    search: (
      <>
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-4-4" />
      </>
    ),
    settings: (
      <>
        <path d="M10 2h4l.6 2.5a7.8 7.8 0 0 1 1.8.8l2.3-1.1 2 3.5-1.9 1.7a8 8 0 0 1 0 2.1l1.9 1.7-2 3.5-2.3-1.1a7.8 7.8 0 0 1-1.8.8L14 21h-4l-.6-2.5a7.8 7.8 0 0 1-1.8-.8l-2.3 1.1-2-3.5 1.9-1.7a8 8 0 0 1 0-2.1L3.3 9.8l2-3.5 2.3 1.1a7.8 7.8 0 0 1 1.8-.8L10 2Z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    shield: (
      <>
        <path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Z" />
        <path d="m9 12 2 2 4-4" />
      </>
    ),
    sparkles: (
      <>
        <path d="m12 3-1.2 3.2L7.5 7.5l3.3 1.3L12 12l1.2-3.2 3.3-1.3-3.3-1.3L12 3Z" />
        <path d="m5 14-.8 2.2L2 17l2.2.8L5 20l.8-2.2L8 17l-2.2-.8L5 14Z" />
      </>
    ),
    trash: (
      <>
        <path d="M3 6h18M8 6V4h8v2M19 6l-1 15H6L5 6M10 10v7M14 10v7" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21a8 8 0 0 1 16 0" />
      </>
    ),
  };

  return (
    <svg
      aria-hidden="true"
      className="icon"
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
    >
      {paths[name]}
    </svg>
  );
}

export const friends = [
  {
    id: 1,
    name: "김서준",
    relation: "친구",
    initial: "준",
    color: "sky",
    streak: 18,
    completed: 3,
    status: "오늘 모두 챙겼어요",
    supplements: [
      {
        id: 11,
        name: "밀크씨슬 실리마린",
        brand: "나우푸드",
        ingredient: "실리마린 300mg",
        amount: "100캡슐 · 3개월분",
        price: "24,900원",
        category: "간 건강",
        color: "leaf",
        time: "아침 식후 1캡슐",
      },
      {
        id: 12,
        name: "올인원 멀티비타민",
        brand: "센트룸",
        ingredient: "비타민 13종 · 미네랄 9종",
        amount: "70정 · 2개월분",
        price: "31,000원",
        category: "종합비타민",
        color: "sun",
        time: "점심 식후 1정",
      },
      {
        id: 13,
        name: "루테인 지아잔틴",
        brand: "안국건강",
        ingredient: "루테인 18.2mg · 지아잔틴 1.8mg",
        amount: "30캡슐 · 1개월분",
        price: "15,900원",
        category: "눈 건강",
        color: "orange",
        time: "저녁 식후 1캡슐",
      },
    ],
  },
  {
    id: 2,
    name: "이유진",
    relation: "회사 동료",
    initial: "유",
    color: "peach",
    streak: 9,
    completed: 2,
    status: "점심 영양제까지 완료",
    supplements: [
      {
        id: 21,
        name: "울트라 비타민D",
        brand: "솔가",
        ingredient: "비타민D3 25㎍",
        amount: "90캡슐 · 3개월분",
        price: "19,800원",
        category: "비타민D",
        color: "sun",
        time: "아침 식후 1캡슐",
      },
      {
        id: 22,
        name: "여성 유산균 프로바이오틱스",
        brand: "락토핏",
        ingredient: "프로바이오틱스 10억 CFU",
        amount: "60포 · 2개월분",
        price: "26,700원",
        category: "장 건강",
        color: "pink",
        time: "아침 식전 1포",
      },
    ],
  },
  {
    id: 3,
    name: "박지호",
    relation: "친구",
    initial: "호",
    color: "mint",
    streak: 24,
    completed: 4,
    status: "24일째 꾸준히 먹는 중",
    supplements: [
      {
        id: 31,
        name: "프로메가 알티지 오메가3",
        brand: "종근당건강",
        ingredient: "EPA+DHA 600mg",
        amount: "60캡슐 · 2개월분",
        price: "22,900원",
        category: "혈행 건강",
        color: "ocean",
        time: "점심 식후 1캡슐",
      },
      {
        id: 32,
        name: "아르기닌 6000",
        brand: "뉴트리디데이",
        ingredient: "L-아르기닌 6,000mg",
        amount: "15포 · 15일분",
        price: "17,500원",
        category: "활력",
        color: "orange",
        time: "운동 전 1포",
      },
      {
        id: 33,
        name: "칼슘 마그네슘 아연",
        brand: "GNC",
        ingredient: "칼슘 333mg · 마그네슘 133mg",
        amount: "90정 · 1개월분",
        price: "29,000원",
        category: "뼈 건강",
        color: "lilac",
        time: "저녁 식후 3정",
      },
      {
        id: 34,
        name: "활성 비타민B 컴플렉스",
        brand: "쏜리서치",
        ingredient: "비타민B군 8종",
        amount: "60캡슐 · 1개월분",
        price: "38,900원",
        category: "에너지",
        color: "sun",
        time: "아침 식후 2캡슐",
      },
    ],
  },
  {
    id: 4,
    name: "최은서",
    relation: "가족",
    initial: "은",
    color: "lilac",
    streak: 6,
    completed: 1,
    status: "아침 영양제 복용 완료",
    supplements: [
      {
        id: 41,
        name: "콜라겐 비오틴 플러스",
        brand: "바이탈뷰티",
        ingredient: "저분자콜라겐 1,000mg",
        amount: "30포 · 1개월분",
        price: "35,000원",
        category: "피부 건강",
        color: "pink",
        time: "저녁 식후 1포",
      },
    ],
  },
];

export const mySupplements = [
  {
    id: 1,
    name: "얼라이브 원스데일리",
    brand: "네이처스웨이",
    ingredient: "비타민 21종 · 미네랄 7종",
    amount: "60정 · 2개월분",
    price: "28,900원",
    category: "종합비타민",
    color: "sun",
    time: "아침 식후 1정",
  },
  {
    id: 2,
    name: "초임계 알티지 오메가3",
    brand: "닥터린",
    ingredient: "EPA+DHA 600mg",
    amount: "30캡슐 · 1개월분",
    price: "21,500원",
    category: "오메가3",
    color: "ocean",
    time: "점심 식후 1캡슐",
  },
  {
    id: 3,
    name: "킬레이트 마그네슘",
    brand: "블루보넷",
    ingredient: "마그네슘 200mg",
    amount: "120캡슐 · 2개월분",
    price: "32,800원",
    category: "미네랄",
    color: "lilac",
    time: "저녁 식전 2캡슐",
  },
];

/**
 * 영양제 병 모양 이미지
 * @param {{ color: string, small?: boolean }} props
 */
export function ProductVisual({ color, small = false }) {
  return (
    <span className={`product-visual ${color} ${small ? "small" : ""}`}>
      <span className="product-cap" />
      <span className="product-label">
        <span>YAKSSOK</span>
        <b>DAILY</b>
      </span>
    </span>
  );
}

/**
 * 영양제 정보 카드 (이미지·카테고리·브랜드·성분·용량·가격·복용 시간)
 * @param {{ supplement: { id: number, name: string, brand: string, ingredient: string, amount: string, price: string, category: string, color: string, time: string } }} props
 */
export function SupplementCard({ supplement }) {
  return (
    <article className="supplement-card">
      <ProductVisual color={supplement.color} />
      <div className="supplement-card-copy">
        <span className="category-chip">{supplement.category}</span>
        <span className="product-brand">{supplement.brand}</span>
        <h3>{supplement.name}</h3>
        <dl className="product-facts">
          <div>
            <dt>영양성분</dt>
            <dd>{supplement.ingredient}</dd>
          </div>
          <div>
            <dt>용량</dt>
            <dd>{supplement.amount}</dd>
          </div>
        </dl>
        <div className="product-bottom">
          <strong>{supplement.price}</strong>
          <span>
            <Icon name="clock" size={14} />
            {supplement.time}
          </span>
        </div>
      </div>
    </article>
  );
}

/**
 * 화면 상단 헤더 (제목, 뒤로 가기, 알림 버튼)
 * @param {{ title: string, subtitle?: string, back?: () => void, onNotifications: () => void }} props
 */
export function TopBar({
  title,
  subtitle,
  back,
  onNotifications,
  actions,
  onSettings,
}) {
  const hasUnread = useContext(UnreadNotificationContext); // 안 읽은 알림이 있을 때만 빨간 점
  return (
    <header className="app-header">
      <div className="header-title">
        {back && (
          <button
            className="round-button back-button"
            aria-label="뒤로 가기"
            onClick={back}
          >
            <Icon name="arrow" />
          </button>
        )}
        <div>
          {subtitle && <span>{subtitle}</span>}
          <h1>{title}</h1>
        </div>
      </div>

      <div className="header-actions">
        {actions}

        {onSettings && (
          <button
            type="button"
            className="round-button settings-button"
            aria-label="설정"
            onClick={onSettings}
          >
            <Icon name="settings" size={21} />
          </button>
        )}

        <button
          type="button"
          className="round-button notification-button"
          aria-label="알림"
          onClick={onNotifications}
        >
          <Icon name="bell" size={21} />
          {hasUnread && <span />}
        </button>
      </div>
    </header>
  );
}
