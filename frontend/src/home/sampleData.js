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
// 오늘 먹은 영양소 예시 (SAMPLE_HOME 영양제 id 1~3의 함량, 권장량은 성인 남성 기준)
const nutrient = (code, name, unit, recommended) => ({ code, name, unit, recommended });
export const SAMPLE_NUTRITION = {
    date: "2026-10-25",
    nutrients: [
        nutrient("VITAMIN_B12", "비타민 B12", "㎍", 2.4),
        nutrient("VITAMIN_C", "비타민 C", "mg", 100),
        nutrient("VITAMIN_D", "비타민 D", "㎍", 10),
        nutrient("CALCIUM", "칼슘", "mg", 800),
        nutrient("MAGNESIUM", "마그네슘", "mg", 370),
        nutrient("ZINC", "아연", "mg", 10),
        nutrient("IRON", "철", "mg", 10),
        nutrient("OMEGA3", "오메가3", "mg", 400),
    ],
    schedules: [
        { scheduleId: 1, amounts: { VITAMIN_B12: 2.4, VITAMIN_C: 100, VITAMIN_D: 10, CALCIUM: 200, MAGNESIUM: 80, ZINC: 8.5 } },
        { scheduleId: 2, amounts: { OMEGA3: 600 } },
        { scheduleId: 3, amounts: { MAGNESIUM: 200 } },
    ],
    unsupportedCount: 0,
    sample: true,
};
