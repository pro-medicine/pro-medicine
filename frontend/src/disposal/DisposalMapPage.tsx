import { useState } from "react";
import { Icon, TopBar } from "../shared/ui";

const pharmacies = [
  {
    id: 1,
    name: "건강온누리약국",
    address: "서울 마포구 월드컵북로 21",
    distance: "180m",
    walk: "도보 3분",
    open: "영업 중 · 21:00 마감",
    accepts: "알약 · 가루약 · 연고",
    x: 52,
    y: 42,
  },
  {
    id: 2,
    name: "상암햇살약국",
    address: "서울 마포구 성암로 267",
    distance: "420m",
    walk: "도보 6분",
    open: "영업 중 · 20:00 마감",
    accepts: "알약 · 가루약 · 물약",
    x: 73,
    y: 24,
  },
  {
    id: 3,
    name: "우리동네365약국",
    address: "서울 마포구 매봉산로 75",
    distance: "650m",
    walk: "도보 9분",
    open: "영업 중 · 22:00 마감",
    accepts: "알약 · 가루약 · 연고 · 물약",
    x: 27,
    y: 67,
  },
  {
    id: 4,
    name: "디지털미디어시티약국",
    address: "서울 마포구 월드컵북로 396",
    distance: "900m",
    walk: "도보 13분",
    open: "영업 종료 · 내일 09:00",
    accepts: "알약 · 가루약",
    x: 80,
    y: 73,
  },
];

export default function DisposalMapScreen({ onNotifications }: { onNotifications: () => void }) {
  const [selected, setSelected] = useState(pharmacies[0]);

  return (
    <div className="screen map-screen">
      <TopBar
        title="폐의약품 지도"
        subtitle="내 주변 안전한 약 폐기"
        onNotifications={onNotifications}
      />

      <section className="map-intro">
        <div className="map-intro-icon">
          <Icon name="trash" size={24} />
        </div>
        <div>
          <h2>처방약은 수거함에 안전하게 버려주세요</h2>
          <p>현재 위치를 중심으로 폐의약품을 받는 가까운 약국을 찾았어요.</p>
        </div>
        <span>4곳 발견</span>
      </section>

      <section className="map-layout">
        <div className="mock-map">
          <div className="map-river" />
          <div className="map-road road-one" />
          <div className="map-road road-two" />
          <div className="map-road road-three" />
          <span className="map-area area-one">상암동</span>
          <span className="map-area area-two">디지털미디어시티</span>
          <span className="map-area area-three">매봉산</span>
          {pharmacies.map((pharmacy, index) => (
            <button
              className={selected.id === pharmacy.id ? "map-marker selected" : "map-marker"}
              key={pharmacy.id}
              onClick={() => setSelected(pharmacy)}
              style={{ left: `${pharmacy.x}%`, top: `${pharmacy.y}%` }}
              aria-label={`${pharmacy.name} 선택`}
            >
              <Icon name="plus" size={17} />
              <small>{index + 1}</small>
            </button>
          ))}
          <div className="my-location">
            <span />
            <i />
          </div>
          <button className="location-button" aria-label="내 위치로 이동">
            <Icon name="pin" size={18} />
          </button>
          <div className="map-selected-card">
            <span className="pharmacy-number">{pharmacies.findIndex((item) => item.id === selected.id) + 1}</span>
            <div>
              <strong>{selected.name}</strong>
              <small>{selected.distance} · {selected.walk}</small>
            </div>
            <span className={selected.open.includes("종료") ? "closed" : "open"}>
              {selected.open.includes("종료") ? "영업 종료" : "영업 중"}
            </span>
          </div>
        </div>

        <div className="pharmacy-list-wrap">
          <div className="section-title-row">
            <div>
              <span className="section-label">NEARBY</span>
              <h2>가까운 수거 약국</h2>
            </div>
            <span className="distance-sort">거리순</span>
          </div>
          <div className="pharmacy-list">
            {pharmacies.map((pharmacy, index) => (
              <button
                className={selected.id === pharmacy.id ? "pharmacy-card selected" : "pharmacy-card"}
                key={pharmacy.id}
                onClick={() => setSelected(pharmacy)}
              >
                <span className="pharmacy-number">{index + 1}</span>
                <span className="pharmacy-info">
                  <span className={pharmacy.open.includes("종료") ? "status closed" : "status"}>
                    {pharmacy.open}
                  </span>
                  <strong>{pharmacy.name}</strong>
                  <small>{pharmacy.address}</small>
                  <span className="accepts">수거 가능 · {pharmacy.accepts}</span>
                </span>
                <span className="pharmacy-distance">
                  <strong>{pharmacy.distance}</strong>
                  <small>{pharmacy.walk}</small>
                  <Icon name="chevron" size={16} />
                </span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <aside className="disposal-guide">
        <span><Icon name="trash" size={19} /></span>
        <div>
          <strong>약 종류별로 이렇게 버려주세요</strong>
          <p>알약은 포장을 제거하고, 가루약은 포장 그대로 모아주세요. 물약과 연고는 새지 않게 밀봉해주세요.</p>
        </div>
        <button>자세히 보기</button>
      </aside>
    </div>
  );
}
