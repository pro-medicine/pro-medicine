import { useEffect, useState } from "react";
import { Icon, TopBar } from "../shared/ui";
import KakaoMap from "./KakaoMap";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export default function DisposalMapScreen({ onNotifications }) {
  const [searchInput, setSearchInput] = useState("강남역 약국");
  const [query, setQuery] = useState("강남역 약국");
  const [pharmacies, setPharmacies] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [guideOpen, setGuideOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function loadPlaces() {
      setLoading(true);
      setError("");

      try {
        if (!API_BASE_URL) {
          throw new Error("VITE_API_BASE_URL 환경변수가 설정되지 않았습니다.");
        }

        const url = new URL("/api/v1/disposal/places", API_BASE_URL);
        url.searchParams.set("query", query);

        const response = await fetch(url.toString(), { signal: controller.signal });
        const result = await response.json();

        if (!response.ok || !result.success) {
          throw new Error(result.message || "약국 검색에 실패했습니다.");
        }

        const documents = Array.isArray(result.data?.documents) ? result.data.documents : [];
        const places = documents
            .map((place) => ({
              id: place.id,
              name: place.place_name,
              address: place.road_address_name || place.address_name,
              phone: place.phone || "",
              latitude: Number(place.y),
              longitude: Number(place.x),
              placeUrl: place.place_url,
            }))
            .filter((place) =>
                Number.isFinite(place.latitude) && Number.isFinite(place.longitude) &&
                place.latitude >= -90 && place.latitude <= 90 &&
                place.longitude >= -180 && place.longitude <= 180
            );

        if (!active) return;
        setPharmacies(places);
        setSelected(places[0] || null);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setPharmacies([]);
        setSelected(null);
        setError(err.message || "약국 정보를 가져오지 못했습니다.");
      } finally {
        if (active) setLoading(false);
      }
    }

    loadPlaces();

    return () => {
      active = false;
      controller.abort();
    };
  }, [query]);

  function handleSearch(event) {
    event.preventDefault();
    const value = searchInput.trim();
    if (!value) return;
    setQuery(value);
  }

  return (
      <div className="screen map-screen">
        <TopBar
            title="폐의약품 지도"
            subtitle="내 주변 안전한 약 폐기"
            onNotifications={onNotifications}
        />

        <section className="map-intro">
          <div className="map-intro-icon"><Icon name="trash" size={24} /></div>
          <div>
            <h2>처방약은 수거함에 안전하게 버려주세요</h2>
            <p>주변 약국을 검색할 수 있어요. 폐의약품 수거 여부는 방문 전 약국에 확인해주세요.</p>
          </div>
          <span>{loading ? "검색 중" : `${pharmacies.length}곳 표시`}</span>
        </section>

        <form className="disposal-search-form" onSubmit={handleSearch}>
          <label htmlFor="disposal-query">약국 검색</label>
          <input
              id="disposal-query"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="예: 강남역 약국"
              maxLength={100}
          />
          <button type="submit">검색</button>
        </form>

        <section className="map-layout">
          <KakaoMap pharmacies={pharmacies} selected={selected} onSelect={setSelected} />

          <div className="pharmacy-list-wrap">
            <div className="section-title-row">
              <div>
                <span className="section-label">PHARMACIES</span>
                <h2>검색된 약국</h2>
              </div>
              <span className="distance-sort">검색 결과</span>
            </div>

            {loading && <p className="disposal-state" role="status">약국 정보를 검색하고 있어요.</p>}
            {error && <p className="disposal-state" role="alert">{error}</p>}
            {!loading && !error && pharmacies.length === 0 && (
                <p className="disposal-state">검색된 약국이 없습니다. 다른 지역을 검색해보세요.</p>
            )}

            <div className="pharmacy-list">
              {pharmacies.map((pharmacy, index) => (
                  <button
                      type="button"
                      className={selected?.id === pharmacy.id ? "pharmacy-card selected" : "pharmacy-card"}
                      key={pharmacy.id}
                      onClick={() => setSelected(pharmacy)}
                  >
                    <span className="pharmacy-number">{index + 1}</span>
                    <span className="pharmacy-info">
                  <strong>{pharmacy.name}</strong>
                  <small>{pharmacy.address}</small>
                      {pharmacy.phone && <small>전화: {pharmacy.phone}</small>}
                      <span className="accepts">폐의약품 수거 여부는 약국에 문의</span>
                </span>
                    <span className="pharmacy-distance"><Icon name="chevron" size={16} /></span>
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
            {guideOpen && <p>배출 장소와 수거 가능한 종류는 지역별로 다를 수 있으므로 해당 지자체나 약국의 안내를 확인해주세요.</p>}
          </div>
          <button type="button" aria-expanded={guideOpen} onClick={() => setGuideOpen((value) => !value)}>
            {guideOpen ? "접기" : "자세히 보기"}
          </button>
        </aside>
      </div>
  );
}
