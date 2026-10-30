// ⚠️ 예시 데이터 (실제 사용자/영양제 정보 아님)
// 백엔드 연결 전 화면 확인용, API 실패 시 대체용으로만 사용한다.

export const SAMPLE_HOME = {
    userName: "SAMPLE DATA",
    today: "2026-10-25",
    hasUnreadNotifications: true,
    streakDays: 12,
    supplements: [
        { id: 1, timing: "아침 식후 1정", name: "얼라이브 원스데일리", desc: "비타민 21종 · 미네랄 7종", color: "#d6b44c", taken: true, mealSlot: "BREAKFAST", remindAt: "08:00", completedAt: "08:10" },
        { id: 2, timing: "점심 식후 1캡슐", name: "초임계 알티지 오메가3", desc: "EPA+DHA 600mg", color: "#6f99a1", taken: false, mealSlot: "LUNCH", remindAt: "12:30", completedAt: null },
        { id: 3, timing: "저녁 식전 2캡슐", name: "킬레이트 마그네슘", desc: "마그네슘 200mg", color: "#a68fb8", taken: false, mealSlot: "DINNER", remindAt: "18:30", completedAt: null },
    ],
};

export const SAMPLE_RECOMMEND = [
    { category: "에너지", name: "활성 비타민B 컴플렉스", desc: "비타민B군 8종", price: 38900, color: "#d6b44c" },
    { category: "미네랄", name: "킬레이트 마그네슘", desc: "마그네슘 200mg", price: 32800, color: "#a68fb8" },
];