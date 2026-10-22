// 홈 상단: 날짜 + 인사말 + 알림(종) 버튼

// 날짜는 항상 대한민국 시간(Asia/Seoul) 기준으로 표시
const KOREA_DATE = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "long",
});

// "2026-10-21" → "10월 21일 수요일"
// 날짜가 없으면 한국 기준 오늘
function formatDate(dateStr) {
    const date = dateStr
        ? new Date(`${dateStr}T00:00:00+09:00`) // 한국 시간 10월 21일 0시로 읽기
        : new Date();                          // 지금 이 순간
    return KOREA_DATE.format(date);
}