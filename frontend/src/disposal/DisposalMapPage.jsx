import { useCallback, useEffect, useRef, useState } from "react";
import { Icon, TopBar } from "../shared/ui";
import KakaoMap from "./KaKaoMap";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || window.location.origin;
const RADII = [500, 1000, 2000, 3000];

function isValidCoordinate(latitude, longitude) {
  return (
      Number.isFinite(latitude) && Number.isFinite(longitude) &&
      latitude >= -90 && latitude <= 90 &&
      longitude >= -180 && longitude <= 180
  );
}

function toLocationCandidate(place) {
  const latitude = Number(place.y);
  const longitude = Number(place.x);

  return {
    id: String(place.id || `${place.x}-${place.y}`),
    name: place.place_name || place.address_name || "",
    address: place.address_name || "",
    latitude,
    longitude,
  };
}

function toPharmacy(place) {
  const latitude = Number(place.y);
  const longitude = Number(place.x);
  const rawDistance = place.distance === "" || place.distance == null
      ? null
      : Number(place.distance);
  const distance = Number.isFinite(rawDistance) && rawDistance >= 0
      ? rawDistance : null;

  return {
    id: String(place.id || `${place.x}-${place.y}`),
    name: place.place_name || "이름 없는 약국",
    address: place.road_address_name || place.address_name || "주소 정보 없음",
    phone: place.phone || "",
    latitude,
    longitude,
    placeUrl: place.place_url || "",
    distance,
    distanceText: distance == null ? "" : distance < 1000
        ? `${Math.round(distance)}m` : `${(distance / 1000).toFixed(1)}km`,
  };
}

// 검색 결과 가운데 입력한 이름과 가장 밀접한 지역을 우선 선택.
// 모호한 이름일 경우 선택된 기준 지역을 목록에서 직접 변경할 수 있다.
function choosePreferredLocation(candidates, query) {
  const keyword = query.trim().replace(/\s*약국\s*$/, "").trim().replace(/\s+/g, "");
  const score = (candidate) => {
    const name = candidate.name.replace(/\s+/g, "");
    const address = candidate.address.replace(/\s+/g, "");
    let points = 0;
    if (name === keyword) points += 100;
    if (name.startsWith(`${keyword}1동`)) points += 110;
    if (name === `${keyword}동`) points += 85;
    if (name === `${keyword}역` || name.startsWith(`${keyword}역`)) points += 80;
    if (name.startsWith(keyword)) points += 45;
    if (name.includes(keyword)) points += 20;
    if (address.includes(keyword)) points += 10;
    if (/주민센터|행정복지센터/.test(name) && name.startsWith(keyword)) points += 18;
    if (/약국/.test(name)) points -= 80;
    return points;
  };
  return [...candidates].sort((a, b) => score(b) - score(a))[0];
}

function getLocationErrorMessage(error) {
  if (error?.code === 1) return "위치 접근이 거부되었습니다. 지역명으로 검색할 수 있어요.";
  if (error?.code === 2) return "현재 위치를 확인할 수 없습니다. GPS 또는 네트워크 설정을 확인해주세요.";
  if (error?.code === 3) return "위치 조회 시간이 초과되었습니다. 다시 시도하거나 지역명으로 검색해주세요.";
  return "현재 위치를 가져오지 못했습니다. 지역명으로 약국을 검색할 수 있어요.";
}

export default function DisposalMapScreen({ onNotifications }) {
  const [searchInput, setSearchInput] = useState("강남역 약국");
  const [query, setQuery] = useState("강남역 약국");
  const [searchCount, setSearchCount] = useState(0);
  // keyword: 검색어로 장소 찾기 → area: 대표 장소 주변 약국 표시 → nearby: GPS 검색
  const [mode, setMode] = useState("keyword");
  const [radius, setRadius] = useState(1000);
  const [locationCandidates, setLocationCandidates] = useState([]);
  const [searchCenter, setSearchCenter] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState("");
  const [pharmacies, setPharmacies] = useState([]);
  const [selected, setSelected] = useState(null);
  const [focusPharmacy, setFocusPharmacy] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [truncated, setTruncated] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);
  const locationRequestId = useRef(0);

  useEffect(() => () => { locationRequestId.current += 1; }, []);

  useEffect(() => {
    if (mode === "nearby" && !userLocation) return;
    if (mode === "area" && !searchCenter) return;

    const controller = new AbortController();
    let active = true;

    async function loadPlaces() {
      setLoading(true);
      setError("");
      setTruncated(false);
      setPharmacies([]);
      setSelected(null);
      setFocusPharmacy(null);
      if (mode === "keyword") setLocationCandidates([]);

      try {
        const findingLocation = mode === "keyword";
        const url = new URL(
            findingLocation ? "/api/v1/disposal/locations" : "/api/v1/disposal/nearby",
            API_BASE_URL
        );

        if (findingLocation) {
          url.searchParams.set("query", query);
        } else {
          const center = mode === "area" ? searchCenter : userLocation;
          url.searchParams.set("latitude", String(center.latitude));
          url.searchParams.set("longitude", String(center.longitude));
          url.searchParams.set("radius", String(radius));
        }

        const response = await fetch(url.toString(), { signal: controller.signal });
        if (!response.ok) {
          throw new Error(`검색 요청에 실패했습니다. (HTTP ${response.status})`);
        }
        const result = await response.json();
        if (!result?.success) {
          throw new Error(result?.message || "검색에 실패했습니다.");
        }
        if (!active) return;

        const documents = Array.isArray(result.data?.documents)
            ? result.data.documents : [];

        if (findingLocation) {
          const seen = new Set();
          const candidates = documents
              .map(toLocationCandidate)
              .filter((place) => place.name && isValidCoordinate(place.latitude, place.longitude))
              .filter((place) => {
                if (seen.has(place.id)) return false;
                seen.add(place.id);
                return true;
              });

          setLocationCandidates(candidates);
          if (candidates.length) {
            // 지역 선택을 강요하지 않고 대표 지역으로 즉시 약국을 조회한다.
            setSearchCenter(choosePreferredLocation(candidates, query));
            setMode("area");
          } else {
            setSearchCenter(null);
          }
        } else {
          const places = documents.map(toPharmacy).filter((place) =>
              isValidCoordinate(place.latitude, place.longitude)
          );
          setPharmacies(places);
          setSelected(places[0] || null);
          setTruncated(Boolean(result.data?.meta?.truncated));
        }
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setError(err?.message || "정보를 가져오지 못했습니다.");
      } finally {
        if (active) setLoading(false);
      }
    }

    loadPlaces();
    return () => {
      active = false;
      controller.abort();
    };
  }, [mode, query, searchCount, radius, userLocation, searchCenter]);

  function handleSearch(event) {
    event.preventDefault();
    const value = searchInput.trim();
    if (!value) return;
    locationRequestId.current += 1;
    setLocating(false);
    setLocationError("");
    setSearchCenter(null);
    setLocationCandidates([]);
    setMode("keyword");
    setQuery(value);
    setSearchCount((count) => count + 1);
  }

  function handleCandidateSelect(place) {
    if (mode !== "area" || searchCenter?.id !== place.id) {
      setSearchCenter(place);
      setMode("area");
    }
    setLocationError("");
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
          const { latitude, longitude } = position.coords;
          if (!isValidCoordinate(latitude, longitude)) {
            setLocationError("유효한 위치 좌표를 가져오지 못했습니다. 지역명으로 검색해주세요.");
            return;
          }
          setUserLocation({ latitude, longitude });
          setSearchCenter(null);
          setLocationCandidates([]);
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
  const area = mode === "area";

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
          <label htmlFor="disposal-query">지역명 검색</label>
          <input
              id="disposal-query"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="예: 발산, 발산역, 강남역 약국"
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
            {nearby
                ? `내 위치 기준 ${radius.toLocaleString()}m 이내 · 거리순`
                : area
                    ? `${searchCenter?.name} 기준 ${radius.toLocaleString()}m 이내 · 거리순`
                    : "검색한 지역의 주변 약국을 찾는 중 · GPS 사용 안 함"}
          </small>
        </div>
        {locationError && (
            <p className="disposal-location-error" role="alert">{locationError}</p>
        )}

        <section className="map-layout">
          <KakaoMap
              pharmacies={pharmacies}
              selected={selected}
              onSelect={handlePharmacySelect}
              mode={mode}
              userLocation={nearby ? userLocation : null}
              searchCenter={area ? searchCenter : null}
              radius={radius}
              focusPharmacy={focusPharmacy}
          />

          <div className="pharmacy-list-wrap">
            <div className="section-title-row">
              <div>
                <span className="section-label">PHARMACIES</span>
                <h2>{nearby ? "내 주변 약국" : "검색 지역 주변 약국"}</h2>
              </div>
              <span className="distance-sort">
              {loading ? "조회 중" : `${pharmacies.length}곳 · 거리순`}
            </span>
            </div>

            {/* 지역 후보는 검색창 아래가 아니라 약국 목록 영역에 표시한다. */}
            {!nearby && locationCandidates.length > 0 && (
                <div className="disposal-location-candidates disposal-location-candidates--sidebar">
                  <h3>검색 기준 지역 {area && searchCenter ? `· ${searchCenter.name}` : ""}</h3>
                  <div className="disposal-candidate-buttons" aria-label="검색 기준 지역 변경">
                    {locationCandidates.map((place) => (
                        <button
                            key={place.id}
                            type="button"
                            className={area && searchCenter?.id === place.id ? "selected" : ""}
                            aria-pressed={area && searchCenter?.id === place.id}
                            onClick={() => handleCandidateSelect(place)}
                            title={place.address}
                        >
                          <strong>{place.name}</strong>
                          {place.address && <small>{place.address}</small>}
                        </button>
                    ))}
                  </div>
                  <p>가장 관련 높은 지역을 자동으로 선택했어요. 다른 지역을 누르면 결과가 바뀝니다.</p>
                </div>
            )}

            {loading && (
                <p className="disposal-state" role="status">
                  {mode === "keyword" ? "지역 위치를 찾고 있어요." : "주변 약국을 검색하고 있어요."}
                </p>
            )}
            {error && <p className="disposal-state" role="alert">{error}</p>}
            {!loading && !error && mode === "keyword" && locationCandidates.length === 0 && (
                <p className="disposal-state">검색된 지역이 없습니다. 역 이름이나 구체적인 주소를 입력해주세요.</p>
            )}
            {!loading && !error && mode !== "keyword" && pharmacies.length === 0 && (
                <p className="disposal-state">반경 {radius.toLocaleString()}m 안에 검색된 약국이 없습니다. 반경을 넓혀보세요.</p>
            )}
            {truncated && (
                <p className="disposal-state">카카오 API의 조회 가능 범위에 도달해 일부 결과만 표시될 수 있습니다.</p>
            )}

            <div className="pharmacy-list">
              {pharmacies.map((pharmacy, index) => (
                  <button
                      key={pharmacy.id}
                      type="button"
                      className={selected?.id === pharmacy.id ? "pharmacy-card selected" : "pharmacy-card"}
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
                  {pharmacy.distanceText && <strong>{pharmacy.distanceText}</strong>}
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
            {guideOpen && (
                <p>배출 장소와 수거 가능한 종류는 지역별로 다를 수 있으므로 해당 지자체나 약국의 안내를 확인해주세요.</p>
            )}
          </div>
          <button type="button" aria-expanded={guideOpen} onClick={() => setGuideOpen((v) => !v)}>
            {guideOpen ? "접기" : "자세히 보기"}
          </button>
        </aside>
      </div>
  );
}
