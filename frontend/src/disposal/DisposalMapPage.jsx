import { useCallback, useEffect, useRef, useState } from "react";
import { Icon, TopBar } from "../shared/ui";
import KakaoMap from "./KaKaoMap";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const RADII = [500, 1000, 2000, 3000];

function toPharmacy(place, nearby) {
  const latitude = Number(place.y);
  const longitude = Number(place.x);
  const rawDistance = nearby && place.distance !== "" && place.distance != null
      ? Number(place.distance)
      : null;
  const distance = rawDistance !== null && Number.isFinite(rawDistance) && rawDistance >= 0
      ? rawDistance
      : null;

  return {
    id: place.id,
    name: place.place_name,
    address: place.road_address_name || place.address_name,
    phone: place.phone || "",
    latitude,
    longitude,
    placeUrl: place.place_url,
    distance,
    distanceText: distance == null ? "" : distance < 1000
        ? `${Math.round(distance)}m`
        : `${(distance / 1000).toFixed(1)}km`,
  };
}

function getLocationErrorMessage(error) {
  if (error?.code === 1) return "위치 접근이 거부되었습니다. 기존 지역명 검색을 이용할 수 있어요.";
  if (error?.code === 2) return "현재 위치를 확인할 수 없습니다. GPS 또는 네트워크 설정을 확인해주세요.";
  if (error?.code === 3) return "위치 조회 시간이 초과되었습니다. 다시 시도하거나 지역명으로 검색해주세요.";
  return "현재 위치를 가져오지 못했습니다. 지역명으로 약국을 검색할 수 있어요.";
}

export default function DisposalMapScreen({ onNotifications }) {
  const [searchInput, setSearchInput] = useState("강남역 약국");
  const [query, setQuery] = useState("강남역 약국");
  const [searchCount, setSearchCount] = useState(0);
  const [mode, setMode] = useState("keyword");
  const [radius, setRadius] = useState(1000);
  const [userLocation, setUserLocation] = useState(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState("");
  const [pharmacies, setPharmacies] = useState([]);
  const [selected, setSelected] = useState(null);
  const [focusPharmacy, setFocusPharmacy] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [guideOpen, setGuideOpen] = useState(false);
  const locationRequestId = useRef(0);

  useEffect(() => {
    return () => { locationRequestId.current += 1; };
  }, []);

  useEffect(() => {
    if (mode === "nearby" && !userLocation) return;

    const controller = new AbortController();
    let active = true;

    async function loadPlaces() {
      setLoading(true);
      setError("");
      setPharmacies([]);
      setSelected(null);
      setFocusPharmacy(null);

      try {
        if (!API_BASE_URL) {
          throw new Error("VITE_API_BASE_URL 환경변수가 설정되지 않았습니다.");
        }

        const url = new URL(
            mode === "nearby" ? "/api/v1/disposal/nearby" : "/api/v1/disposal/places",
            API_BASE_URL
        );

        if (mode === "nearby") {
          url.searchParams.set("latitude", String(userLocation.latitude));
          url.searchParams.set("longitude", String(userLocation.longitude));
          url.searchParams.set("radius", String(radius));
        } else {
          url.searchParams.set("query", query);
        }

        const response = await fetch(url.toString(), { signal: controller.signal });
        if (!response.ok) {
          throw new Error(`약국 검색 요청에 실패했습니다. (HTTP ${response.status})`);
        }

        let result;
        try {
          result = await response.json();
        } catch {
          throw new Error("서버 응답을 처리할 수 없습니다.");
        }

        if (!result?.success) {
          throw new Error(result?.message || "약국 검색에 실패했습니다.");
        }

        const documents = Array.isArray(result.data?.documents) ? result.data.documents : [];
        const places = documents
            .map((place) => toPharmacy(place, mode === "nearby"))
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
        setError(err?.message || "약국 정보를 가져오지 못했습니다.");
      } finally {
        if (active) setLoading(false);
      }
    }

    loadPlaces();
    return () => {
      active = false;
      controller.abort();
    };
  }, [mode, query, searchCount, radius, userLocation]);

  function handleSearch(event) {
    event.preventDefault();
    const value = searchInput.trim();
    if (!value) return;

    locationRequestId.current += 1;
    setLocating(false);
    setLocationError("");
    setMode("keyword");
    setQuery(value);
    setSearchCount((count) => count + 1);
  }

  function handleNearbySearch() {
    if (locating) return;

    if (!navigator.geolocation || !window.isSecureContext) {
      setLocationError("위치 기능을 사용할 수 없습니다. HTTPS 또는 localhost 환경에서 실행해주세요.");
      return;
    }

    const requestId = ++locationRequestId.current;
    setLocating(true);
    setLocationError("");

    navigator.geolocation.getCurrentPosition(
        (position) => {
          if (requestId !== locationRequestId.current) return;
          setLocating(false);
          const latitude = position.coords.latitude;
          const longitude = position.coords.longitude;
          if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
            setLocationError("유효한 위치 좌표를 가져오지 못했습니다. 지역명으로 검색해주세요.");
            return;
          }
          setUserLocation({ latitude, longitude });
          setMode("nearby");
        },
        (positionError) => {
          if (requestId !== locationRequestId.current) return;
          setLocating(false);
          setLocationError(getLocationErrorMessage(positionError));
        },
        { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 }
    );
  }

  const handlePharmacySelect = useCallback((pharmacy) => {
    setSelected(pharmacy);
    setFocusPharmacy({ latitude: pharmacy.latitude, longitude: pharmacy.longitude });
  }, []);

  const nearby = mode === "nearby";

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
            <p>지역명을 검색하거나 내 주변 약국을 찾아보세요. 폐의약품 수거 여부는 방문 전 약국에 확인해주세요.</p>
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

        <div className="disposal-location-controls">
          <button
              className="disposal-nearby-button"
              type="button"
              onClick={handleNearbySearch}
              disabled={locating}
              aria-busy={locating}
          >
            <Icon name="pin" size={16} />
            {locating ? "현재 위치 확인 중..." : "내 주변 약국"}
          </button>
          <label className="disposal-radius-label" htmlFor="disposal-radius">
            검색 반경
            <select
                id="disposal-radius"
                value={radius}
                onChange={(event) => {
                  const nextRadius = Number(event.target.value);
                  if (RADII.includes(nextRadius)) setRadius(nextRadius);
                }}
            >
              <option value={500}>500m</option>
              <option value={1000}>1km</option>
              <option value={2000}>2km</option>
              <option value={3000}>3km</option>
            </select>
          </label>
          <small className="disposal-search-mode" aria-live="polite">
            {nearby ? `내 위치 기준 ${radius.toLocaleString()}m 이내 · 거리순` : "지역명 검색 중 · 위치 정보 사용 안 함"}
          </small>
        </div>
        {locationError && <p className="disposal-location-error" role="alert">{locationError}</p>}

        <section className="map-layout">
          <KakaoMap
              pharmacies={pharmacies}
              selected={selected}
              onSelect={handlePharmacySelect}
              mode={mode}
              userLocation={nearby ? userLocation : null}
              radius={radius}
              focusPharmacy={focusPharmacy}
          />

          <div className="pharmacy-list-wrap">
            <div className="section-title-row">
              <div>
                <span className="section-label">PHARMACIES</span>
                <h2>{nearby ? "내 주변 약국" : "검색된 약국"}</h2>
              </div>
              <span className="distance-sort">{nearby ? "직선거리순" : "검색 결과"}</span>
            </div>

            {loading && <p className="disposal-state" role="status">약국 정보를 검색하고 있어요.</p>}
            {error && <p className="disposal-state" role="alert">{error}</p>}
            {!loading && !error && pharmacies.length === 0 && (
                <p className="disposal-state">
                  {nearby
                      ? `${radius.toLocaleString()}m 이내 검색된 약국이 없습니다. 반경을 넓혀보세요.`
                      : "검색된 약국이 없습니다. 다른 지역을 검색해보세요."}
                </p>
            )}

            <div className="pharmacy-list">
              {pharmacies.map((pharmacy, index) => (
                  <button
                      type="button"
                      className={selected?.id === pharmacy.id ? "pharmacy-card selected" : "pharmacy-card"}
                      key={pharmacy.id}
                      onClick={() => handlePharmacySelect(pharmacy)}
                  >
                    <span className="pharmacy-number">{index + 1}</span>
                    <span className="pharmacy-info">
                  <strong>{pharmacy.name}</strong>
                  <small>{pharmacy.address}</small>
                      {pharmacy.phone && <small>전화: {pharmacy.phone}</small>}
                      <span className="accepts">폐의약품 수거 여부는 약국에 문의</span>
                </span>
                    <span className="pharmacy-distance">
                  {nearby && pharmacy.distanceText && <strong>{pharmacy.distanceText}</strong>}
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
            {guideOpen && <p>배출 장소와 수거 가능한 종류는 지역별로 다를 수 있으므로 해당 지자체나 약국의 안내를 확인해주세요.</p>}
          </div>
          <button type="button" aria-expanded={guideOpen} onClick={() => setGuideOpen((value) => !value)}>
            {guideOpen ? "접기" : "자세히 보기"}
          </button>
        </aside>
      </div>
  );
}
