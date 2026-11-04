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

export default function DisposalMapScreen({ onNotifications, onRegisterBack }) {
  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const [searchCount, setSearchCount] = useState(0);
  // keyword: 검색어로 장소 찾기 → area: 대표 장소 주변 약국 표시 → nearby: GPS 검색
  const [mode, setMode] = useState("nearby");
  const [radius, setRadius] = useState(1000);
  const [locationCandidates, setLocationCandidates] = useState([]);
  const [searchCenter, setSearchCenter] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [lastValidMap, setLastValidMap] = useState(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState("");
  const [pharmacies, setPharmacies] = useState([]);
  const [selected, setSelected] = useState(null);
  const [focusPharmacy, setFocusPharmacy] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [truncated, setTruncated] = useState(false);
  const locationRequestId = useRef(0);
  const walkingRouteRequestRef = useRef(null);
  const walkingRouteRequestId = useRef(0);
  const [walkingRouteOrigin, setWalkingRouteOrigin] = useState(null);
  const [isNavigating, setIsNavigating] = useState(false);
  const [liveLocation, setLiveLocation] = useState(null);
  const [trackingError, setTrackingError] = useState("");
  const [walkingRoute, setWalkingRoute] = useState(null);
  const [walkingRouteLoading, setWalkingRouteLoading] = useState(false);
  const [walkingRouteError, setWalkingRouteError] = useState("");

  const cancelWalkingRoute = useCallback(() => {
    walkingRouteRequestId.current += 1;
    walkingRouteRequestRef.current?.abort();
    walkingRouteRequestRef.current = null;
    setWalkingRoute(null);
    setWalkingRouteOrigin(null);
    setWalkingRouteLoading(false);
    setWalkingRouteError("");
    setIsNavigating(false);
    setLiveLocation(null);
    setTrackingError("");
  }, []);

  useEffect(() => {
    if (!onRegisterBack) return undefined;
    const close = selected || isNavigating || walkingRoute
      ? () => {
          cancelWalkingRoute();
          setSelected(null);
          setFocusPharmacy(null);
        }
      : null;
    onRegisterBack("disposal", close);
    return () => onRegisterBack("disposal", null);
  }, [selected, isNavigating, walkingRoute, cancelWalkingRoute, onRegisterBack]);

  useEffect(() => () => {
    locationRequestId.current += 1;
    walkingRouteRequestId.current += 1;
    walkingRouteRequestRef.current?.abort();
  }, []);

  useEffect(() => {
    if (!navigator.geolocation || !window.isSecureContext) {
      setLocationError("위치 기능을 사용할 수 없습니다. HTTPS 또는 localhost 환경에서 실행하거나 지역명으로 검색해주세요.");
      return;
    }

    const requestId = ++locationRequestId.current;
    setLocating(true);

    navigator.geolocation.getCurrentPosition(
        (position) => {
          if (requestId !== locationRequestId.current) return;
          setLocating(false);
          const { latitude, longitude } = position.coords;
          if (!isValidCoordinate(latitude, longitude)) {
            setLocationError("유효한 위치 좌표를 가져오지 못했습니다. 지역명으로 검색해주세요.");
            return;
          }
          const nextLocation = { latitude, longitude };
          setUserLocation(nextLocation);
          setLastValidMap({ mode: "nearby", center: nextLocation });
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

    return () => {
      if (requestId === locationRequestId.current) locationRequestId.current += 1;
    };
  }, []);

  const lookupRadius = mode === "keyword" ? null : radius;

  useEffect(() => {
    if (mode === "nearby" && !userLocation) return;
    if (mode === "area" && !searchCenter) return;

    const controller = new AbortController();
    let active = true;

    async function loadPlaces() {
      cancelWalkingRoute();
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
          url.searchParams.set("radius", String(lookupRadius));
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
            const nextCenter = choosePreferredLocation(candidates, query);
            setSearchCenter(nextCenter);
            setLastValidMap({ mode: "area", center: nextCenter });
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
  }, [mode, query, searchCount, lookupRadius, userLocation, searchCenter, cancelWalkingRoute]);

  function handleSearch(event) {
    event.preventDefault();
    const value = searchInput.trim();
    if (!value) return;
    cancelWalkingRoute();
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
    cancelWalkingRoute();
    locationRequestId.current += 1;
    setLocating(false);

    if (mode !== "area" || searchCenter?.id !== place.id) {
      setSearchCenter(place);
      setLastValidMap({ mode: "area", center: place });
      setMode("area");
    }
    setLocationError("");
  }

  const handleSearchThisArea = useCallback((center) => {
    if (!isValidCoordinate(center.latitude, center.longitude)) return;
    cancelWalkingRoute();
    locationRequestId.current += 1;
    setLocating(false);
    setLocationError("");
    setLocationCandidates([]);
    setFocusPharmacy(null);
    const nextCenter = {
      id: "map-center",
      name: "지도 중심",
      latitude: center.latitude,
      longitude: center.longitude,
    };
    setSearchCenter(nextCenter);
    setLastValidMap({ mode: "area", center: nextCenter });
    setMode("area");
  }, [cancelWalkingRoute]);

   function handleNearbySearch() {
    if (locating) return;
    if (!navigator.geolocation || !window.isSecureContext) {
      setLocationError("위치 기능을 사용할 수 없습니다. HTTPS 또는 localhost 환경에서 실행해주세요.");
      return;
    }

    cancelWalkingRoute();
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
          const nextLocation = { latitude, longitude };
          setUserLocation(nextLocation);
          setLastValidMap({ mode: "nearby", center: nextLocation });
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

  function startWalkingNavigation() {
    if (walkingRoute?.status !== "OK") return;
    if (!navigator.geolocation || !window.isSecureContext) {
      setTrackingError("실시간 보행 안내는 HTTPS 또는 localhost에서 위치 권한이 필요합니다.");
      return;
    }
    setLiveLocation(null);
    setTrackingError("");
    setIsNavigating(true);
  }

  function stopWalkingNavigation() {
    setIsNavigating(false);
    setLiveLocation(null);
    setTrackingError("");
  }

  useEffect(() => {
    if (!isNavigating || walkingRoute?.status !== "OK") return;

    let active = true;
    let directionAnchor = null;
    let lastHeading = null;
    const toRadians = (value) => value * Math.PI / 180;
    const distanceMeters = (from, to) => {
      const latDelta = toRadians(to.latitude - from.latitude);
      const lngDelta = toRadians(to.longitude - from.longitude);
      const a = Math.sin(latDelta / 2) ** 2 +
          Math.cos(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) *
          Math.sin(lngDelta / 2) ** 2;
      return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    };
    const bearingDegrees = (from, to) => {
      const y = Math.sin(toRadians(to.longitude - from.longitude)) *
          Math.cos(toRadians(to.latitude));
      const x = Math.cos(toRadians(from.latitude)) * Math.sin(toRadians(to.latitude)) -
          Math.sin(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) *
          Math.cos(toRadians(to.longitude - from.longitude));
      return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    };

    const watchId = navigator.geolocation.watchPosition(
        (position) => {
          if (!active) return;
          const { latitude, longitude, accuracy, speed } = position.coords;
          if (!isValidCoordinate(latitude, longitude)) {
            setTrackingError("유효한 GPS 좌표를 확인하지 못했습니다.");
            return;
          }
          if (Number.isFinite(accuracy) && accuracy > 150) {
            setTrackingError("GPS 오차가 큽니다. 위치 신호가 좋아질 때까지 확인 중입니다.");
            return;
          }

          const next = { latitude, longitude };
          let heading = Number.isFinite(position.coords.heading) &&
              (speed == null || speed >= 0.35) ? position.coords.heading : null;
          if (!directionAnchor) directionAnchor = next;
          if (heading == null) {
            const travelled = distanceMeters(directionAnchor, next);
            if (travelled >= Math.max(6, Math.min(12, accuracy || 8))) {
              heading = bearingDegrees(directionAnchor, next);
              directionAnchor = next;
            } else {
              heading = lastHeading;
            }
          } else {
            directionAnchor = next;
          }
          if (heading != null) lastHeading = heading;

          setLiveLocation({
            latitude,
            longitude,
            heading,
            accuracy: Number.isFinite(accuracy) ? accuracy : null,
          });
          setTrackingError("");
        },
        (locationError) => {
          if (!active) return;
          setTrackingError(getLocationErrorMessage(locationError));
          if (locationError?.code === 1) setIsNavigating(false);
        },
        { enableHighAccuracy: true, maximumAge: 1000, timeout: 15000 }
    );

    return () => {
      active = false;
      navigator.geolocation.clearWatch(watchId);
    };
  }, [isNavigating, walkingRoute?.status]);

  async function handleWalkingRoute() {
    if (!selected || walkingRouteLoading) return;

    cancelWalkingRoute();
    const requestId = walkingRouteRequestId.current;
    setWalkingRouteLoading(true);
    let controller = null;

    try {
      if (!navigator.geolocation || !window.isSecureContext) {
        throw new Error("도보 길찾기는 HTTPS 또는 localhost에서 현재 위치 권한이 필요합니다.");
      }

      const position = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 12000,
          maximumAge: 0,
        });
      });

      if (requestId !== walkingRouteRequestId.current) return;

      const origin = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      };
      if (!isValidCoordinate(origin.latitude, origin.longitude)) {
        throw new Error("현재 위치의 좌표가 올바르지 않아 도보 경로를 계산할 수 없습니다.");
      }

      controller = new AbortController();
      walkingRouteRequestRef.current = controller;
      const url = new URL("/api/v1/disposal/walking-route", API_BASE_URL);
      url.searchParams.set("startLatitude", String(origin.latitude));
      url.searchParams.set("startLongitude", String(origin.longitude));
      url.searchParams.set("endLatitude", String(selected.latitude));
      url.searchParams.set("endLongitude", String(selected.longitude));

      const response = await fetch(url.toString(), { signal: controller.signal });
      if (requestId !== walkingRouteRequestId.current || controller.signal.aborted) return;
      if (!response.ok) throw new Error(`도보 경로 조회에 실패했습니다. (HTTP ${response.status})`);
      const result = await response.json();
      if (requestId !== walkingRouteRequestId.current || controller.signal.aborted) return;
      if (!result?.success) throw new Error(result?.message || "도보 경로를 가져오지 못했습니다.");

      const data = result.data;
      if (data?.status !== "OK") {
        const reasons = {
          SAME_POINT: "현재 위치와 약국이 동일한 위치입니다.",
          START_LINK_NOT_FOUND: "현재 위치 주변의 보행로를 찾지 못했습니다.",
          END_LINK_NOT_FOUND: "약국 주변의 보행로를 찾지 못했습니다.",
          TOO_FAR_AWAY: "현재 위치에서 약국까지 거리가 너무 멉니다.",
          ROUTE_RESULT_NOT_FOUND: "이 구간에서 도보 경로를 찾지 못했습니다.",
        };
        throw new Error(reasons[data?.status] || "도보 경로를 찾을 수 없습니다.");
      }

      const hasPath = data.route?.legs?.some((leg) =>
          leg.steps?.some((step) => Array.isArray(step.path?.points) && step.path.points.length >= 2)
      );
      if (!hasPath) throw new Error("표시할 도보 경로 좌표가 없습니다.");

      setWalkingRouteOrigin(origin);
      setWalkingRoute(data);
    } catch (err) {
      if (requestId !== walkingRouteRequestId.current || controller?.signal.aborted) return;
      if (err?.code === 1) {
        setWalkingRouteError("현재 위치 권한이 거부되었습니다. 브라우저 위치 권한을 허용해주세요.");
      } else if (err?.code === 2) {
        setWalkingRouteError("현재 위치를 확인할 수 없습니다. GPS 또는 네트워크를 확인해주세요.");
      } else if (err?.code === 3) {
        setWalkingRouteError("현재 위치 조회 시간이 초과되었습니다. 다시 시도해주세요.");
      } else {
        setWalkingRouteError(err?.message || "현재 위치 기반 도보 경로를 가져오지 못했습니다.");
      }
    } finally {
      if (requestId === walkingRouteRequestId.current) {
        if (walkingRouteRequestRef.current === controller) {
          walkingRouteRequestRef.current = null;
        }
        setWalkingRouteLoading(false);
      }
    }
  }

  const handlePharmacySelect = useCallback((pharmacy) => {
    cancelWalkingRoute();
    setSelected(pharmacy);
    setFocusPharmacy({ latitude: pharmacy.latitude, longitude: pharmacy.longitude });
  }, [cancelWalkingRoute]);

  const nearby = mode === "nearby";
  const area = mode === "area";
  const mapMode = mode === "keyword" ? lastValidMap?.mode : mode;
  const mapUserLocation = mapMode === "nearby"
      ? (mode === "keyword" ? lastValidMap?.center : userLocation) : null;
  const mapSearchCenter = mapMode === "area"
      ? (mode === "keyword" ? lastValidMap?.center : searchCenter) : null;
  const mapCenterAvailable = Boolean(mapUserLocation || mapSearchCenter);

  return (
      <div className="screen map-screen">
        <TopBar
            title="폐의약품 지도"
            subtitle="내 주변 안전한 약 폐기"
            description="처방약은 수거함에 안전하게 버려주세요."
            onNotifications={onNotifications}
        />

        {/* 검색줄: 지역 검색 · 내 주변 약국 · 반경 */}
        <div className="disposal-toolbar">
          <form className="disposal-search-form" onSubmit={handleSearch}>
            <label htmlFor="disposal-query" className="sr-only">약국 검색</label>
            <span className="disposal-search-input">
              <Icon name="search" size={17} />
              <input
                  id="disposal-query"
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder="예: 발산, 발산역, 강남역 약국"
                  maxLength={100}
              />
            </span>
            <button type="submit" className="btn btn-dark">검색</button>
          </form>

          <button
              className="btn btn-outline disposal-nearby-button"
              type="button"
              onClick={handleNearbySearch}
              disabled={locating}
              aria-busy={locating}
          >
            <Icon name="pin" size={16} />
            {locating ? "현재 위치 확인 중..." : "내 주변 약국"}
          </button>
          <label className="disposal-radius-label" htmlFor="disposal-radius">
            반경
            <select
                id="disposal-radius"
                value={radius}
                onChange={(event) => {
                  const nextRadius = Number(event.target.value);
                  if (!RADII.includes(nextRadius)) return;
                  if (mode === "keyword" && lastValidMap && !loading) {
                    if (lastValidMap.mode === "area") {
                      setSearchCenter(lastValidMap.center);
                    } else if (lastValidMap.mode === "nearby") {
                      setUserLocation(lastValidMap.center);
                    }
                    setMode(lastValidMap.mode);
                  }
                  setRadius(nextRadius);
                }}
            >
              <option value={500}>500m</option>
              <option value={1000}>1km</option>
              <option value={2000}>2km</option>
              <option value={3000}>3km</option>
            </select>
          </label>
        </div>
        <small className="disposal-search-mode" aria-live="polite">
          {nearby
              ? `내 위치 기준 ${radius.toLocaleString()}m 이내 · 거리순`
              : area
                  ? `${searchCenter?.name} 기준 ${radius.toLocaleString()}m 이내 · 거리순`
                  : "검색한 지역의 주변 약국을 찾는 중 · GPS 사용 안 함"}
        </small>
        {locationError && <p className="disposal-location-error" role="alert">{locationError}</p>}

        {/* 지도(왼쪽) + 약국 목록(오른쪽) */}
        <section className="map-layout">
          <div className="map-column">
            {mapCenterAvailable ? (
              <KakaoMap
                pharmacies={pharmacies}
                selected={selected}
                onSelect={handlePharmacySelect}
                onSearchThisArea={handleSearchThisArea}
                mode={mapMode}
                userLocation={mapUserLocation}
                searchCenter={mapSearchCenter}
                radius={radius}
                focusPharmacy={focusPharmacy}
                walkingRoute={walkingRoute}
                walkingRouteOrigin={walkingRouteOrigin}
                isNavigating={isNavigating}
                liveLocation={liveLocation}
                trackingError={trackingError}
                onNavigationStart={startWalkingNavigation}
                onNavigationStop={stopWalkingNavigation}
                walkingRouteLoading={walkingRouteLoading}
                walkingRouteError={walkingRouteError}
                onWalkingRoute={handleWalkingRoute}
                onClearWalkingRoute={cancelWalkingRoute}
              />
            ) : (
              <div className="kakao-map-frame">
                <div className="map-overlay-state" role="status">
                  {locating
                      ? "현재 위치를 확인하는 중입니다..."
                      : mode === "keyword" && loading
                          ? "검색 지역을 확인하는 중입니다..."
                          : "위치 권한을 허용하거나 지역명을 검색해주세요."}
                </div>
              </div>
            )}
            <p className="disposal-note">ⓘ 폐의약품 수거 여부는 방문 전 약국에 확인해 주세요.</p>
          </div>

          <div className="pharmacy-list-wrap">
            <div className="pharmacy-list-head">
              <h2>{nearby ? "내 주변 약국" : "검색 지역 주변 약국"}</h2>
              <span>{loading ? "검색 중" : `${pharmacies.length}곳`}</span>
              {mode !== "keyword" && <small>직선거리순</small>}
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
            {!loading && !error && mode !== "keyword" && mapCenterAvailable && pharmacies.length === 0 && (
                <p className="disposal-state">반경 {radius.toLocaleString()}m 안에 검색된 약국이 없습니다. 반경을 넓혀보세요.</p>
            )}
            {truncated && (
                <p className="disposal-state">카카오 API의 조회 가능 범위에 도달해 일부 결과만 표시될 수 있습니다.</p>
            )}

            <div className="pharmacy-list">
              {pharmacies.map((pharmacy) => (
                  <button
                      key={pharmacy.id}
                      type="button"
                      className={selected?.id === pharmacy.id ? "pharmacy-card selected" : "pharmacy-card"}
                      onClick={() => handlePharmacySelect(pharmacy)}
                  >
                    <span className="pharmacy-card-top">
                      <strong>{pharmacy.name}</strong>
                      {pharmacy.distanceText && <b>{pharmacy.distanceText}</b>}
                    </span>
                    <small>{pharmacy.address}</small>
                    {pharmacy.phone && <small className="pharmacy-phone">☎ {pharmacy.phone}</small>}
                  </button>
              ))}
            </div>
          </div>
        </section>

        {/* 약 종류별 버리는 방법 */}
        <section className="disposal-guide" aria-label="약 종류별 버리는 방법">
          <h2>약 종류별로 이렇게 버려주세요</h2>
          <div className="disposal-guide-grid">
            <div>
              <strong>알약</strong>
              <p>포장을 제거하고 알약만 모아주세요.</p>
            </div>
            <div>
              <strong>가루약</strong>
              <p>포장 그대로 모아주세요.</p>
            </div>
            <div>
              <strong>물약 · 연고</strong>
              <p>새지 않게 밀봉해서 가져가 주세요.</p>
            </div>
          </div>
          <p className="disposal-guide-foot">
            배출 장소와 수거 가능한 종류는 지역별로 다를 수 있으므로 해당 지자체나 약국의 안내를 확인해주세요.
          </p>
        </section>
      </div>
  );
}
