import { useEffect, useRef, useState } from "react";

const JAVASCRIPT_KEY = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY;
let sdkPromise = null;

function loadKakaoMap() {
    if (!JAVASCRIPT_KEY) {
        return Promise.reject(
            new Error("VITE_KAKAO_JAVASCRIPT_KEY가 설정되지 않았습니다.")
        );
    }

    if (window.kakao?.maps?.Map) {
        return Promise.resolve(window.kakao.maps);
    }

    if (!sdkPromise) {
        sdkPromise = new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(JAVASCRIPT_KEY)}&autoload=false`;
            script.async = true;
            script.onload = () => {
                if (!window.kakao?.maps?.load) {
                    reject(new Error("카카오맵 SDK를 초기화하지 못했습니다."));
                    return;
                }
                window.kakao.maps.load(() => resolve(window.kakao.maps));
            };
            script.onerror = () => reject(new Error("카카오맵 스크립트를 불러오지 못했습니다."));
            document.head.appendChild(script);
        }).catch((error) => {
            sdkPromise = null;
            throw error;
        });
    }

    return sdkPromise;
}

export default function KakaoMap({
                                     pharmacies = [],
                                     selected,
                                     onSelect,
                                     mode,
                                     userLocation,
                                     searchCenter,
                                     radius = 1000,
                                     focusPharmacy,
                                     walkingRoute,
                                     walkingRouteOrigin,
                                     walkingRouteLoading,
                                     walkingRouteError,
                                     onWalkingRoute,
                                     onClearWalkingRoute,
                                     isNavigating,
                                     liveLocation,
                                     trackingError,
                                     onNavigationStart,
                                     onNavigationStop,
                                     onSearchThisArea,
                                 }) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState("");
    const [showSearchHere, setShowSearchHere] = useState(false);
    const [isFollowing, setIsFollowing] = useState(true);

    useEffect(() => {
        if (isNavigating) {
            setIsFollowing(true);
            setShowSearchHere(false);
        }
    }, [isNavigating]);

    // 지역명 선택은 searchCenter, GPS 검색은 userLocation을 기준으로 함.
    const centerLocation =
        mode === "nearby"
            ? userLocation
            : mode === "area"
                ? searchCenter
                : null;

    // 카카오맵 SDK 로드 및 지도 초기화
    useEffect(() => {
        let cancelled = false;

        loadKakaoMap()
            .then((maps) => {
                if (cancelled || !containerRef.current) return;

                mapRef.current = new maps.Map(containerRef.current, {
                    center: new maps.LatLng(centerLocation.latitude, centerLocation.longitude),
                    level: 4,
                });
                setReady(true);
                setError("");
            })
            .catch((err) => {
                if (!cancelled) setError(err.message);
            });

        return () => {
            cancelled = true;
            mapRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (!ready || !mapRef.current) return;
        const maps = window.kakao.maps;
        const map = mapRef.current;
        const onDragEnd = () => {
            if (isNavigating) setIsFollowing(false);
            else setShowSearchHere(true);
        };
        maps.event.addListener(map, "dragend", onDragEnd);
        return () => maps.event.removeListener(map, "dragend", onDragEnd);
    }, [ready, isNavigating]);

    useEffect(() => {
        setShowSearchHere(false);
    }, [mode, centerLocation?.latitude, centerLocation?.longitude]);

    function handleSearchHereClick() {
        if (!mapRef.current) return;
        const center = mapRef.current.getCenter();
        onSearchThisArea?.({ latitude: center.getLat(), longitude: center.getLng() });
        setShowSearchHere(false);
    }

    // 약국 마커 생성. 약국 목록이 갱신되면 이전 마커는 정리.
    useEffect(() => {
        if (!ready || !mapRef.current) return;

        const maps = window.kakao.maps;
        const map = mapRef.current;
        const highlightedSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="44" height="56" viewBox="0 0 44 56"><path d="M22 3 C10 3 2 12 2 24 C2 38 22 53 22 53 S42 38 42 24 C42 12 34 3 22 3Z" fill="#e53935" stroke="white" stroke-width="3"/><circle cx="22" cy="23" r="8" fill="white"/></svg>';
        const highlightedImage = new maps.MarkerImage(
            `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(highlightedSvg)}`,
            new maps.Size(44, 56),
            { offset: new maps.Point(22, 53) }
        );
        const visiblePharmacies = walkingRoute?.status === "OK"
            ? pharmacies.filter((pharmacy) => pharmacy.id === selected?.id)
            : pharmacies;
        const markers = visiblePharmacies.map((pharmacy) => {
            const isSelected = pharmacy.id === selected?.id;
            const marker = new maps.Marker({
                ...(isSelected ? { image: highlightedImage, zIndex: 30 } : {}),
                map,
                position: new maps.LatLng(pharmacy.latitude, pharmacy.longitude),
                title: pharmacy.name,
                clickable: true,
            });
            const handleClick = () => onSelect?.(pharmacy);
            maps.event.addListener(marker, "click", handleClick);
            return { marker, handleClick };
        });

        return () => {
            markers.forEach(({ marker, handleClick }) => {
                maps.event.removeListener(marker, "click", handleClick);
                marker.setMap(null);
            });
        };
    }, [ready, pharmacies, onSelect, selected?.id, walkingRoute?.status]);

    // 검색 기준 위치에 반경 원과 기준점 표시. 선택한 좌표가 중심이 되도록 이동.
    useEffect(() => {
        if (!ready || !mapRef.current || !centerLocation) return;

        const maps = window.kakao.maps;
        const map = mapRef.current;
        const { latitude, longitude } = centerLocation;
        const center = new maps.LatLng(latitude, longitude);

        const circle = new maps.Circle({
            center,
            radius,
            strokeWeight: 2,
            strokeColor: "#3b7bb6",
            strokeOpacity: 0.8,
            strokeStyle: "solid",
            fillColor: "#78b8f2",
            fillOpacity: 0.12,
            map,
        });

        const centerOverlay = new maps.CustomOverlay({
            map,
            position: center,
            content:
                mode === "nearby"
                    ? '<div class="kakao-my-location" aria-label="내 현재 위치"><span></span></div>'
                    : '<div class="kakao-search-center" aria-label="지역 검색 기준 위치"><span></span></div>',
            xAnchor: 0.5,
            yAnchor: 0.5,
            zIndex: 5,
        });

        // 선택한 반경 전체가 지도에 들어오도록 경계를 설정.
        const latitudeDelta = radius / 111320;
        const cosine = Math.cos((latitude * Math.PI) / 180);
        const longitudeDelta =
            radius / (111320 * Math.max(Math.abs(cosine), 0.01));
        const bounds = new maps.LatLngBounds(
            new maps.LatLng(latitude - latitudeDelta, longitude - longitudeDelta),
            new maps.LatLng(latitude + latitudeDelta, longitude + longitudeDelta)
        );
        map.setBounds(bounds, 35, 30, 35, 30);

        return () => {
            circle.setMap(null);
            centerOverlay.setMap(null);
        };
    }, [ready, mode, centerLocation, radius]);

    useEffect(() => {
        if (!ready || !mapRef.current || walkingRoute?.status !== "OK") return;

        const maps = window.kakao.maps;
        const points = (walkingRoute.route?.legs ?? [])
            .flatMap((leg) => leg.steps ?? [])
            .flatMap((step) => step.path?.points ?? [])
            .filter((point) => Array.isArray(point) && point.length >= 2 &&
                Number.isFinite(point[0]) && Number.isFinite(point[1]))
            .map(([longitude, latitude]) => new maps.LatLng(latitude, longitude));

        if (points.length < 2) return;

        const polyline = new maps.Polyline({
            map: mapRef.current,
            path: points,
            strokeWeight: 5,
            strokeColor: "#2563eb",
            strokeOpacity: 0.9,
            strokeStyle: "solid",
            zIndex: 10,
        });

        const bounds = new maps.LatLngBounds();
        points.forEach((point) => bounds.extend(point));
        mapRef.current.setBounds(bounds, 45, 45, 45, 45);

        return () => polyline.setMap(null);
    }, [ready, walkingRoute]);

    useEffect(() => {
        if (!ready || !mapRef.current || walkingRoute?.status !== "OK" || !walkingRouteOrigin || isNavigating) return;

        const maps = window.kakao.maps;
        const marker = new maps.CustomOverlay({
            map: mapRef.current,
            position: new maps.LatLng(walkingRouteOrigin.latitude, walkingRouteOrigin.longitude),
            content: '<div class="kakao-route-start"><span></span>내 위치 (출발)</div>',
            xAnchor: 0.5,
            yAnchor: 1.2,
            zIndex: 15,
        });

        return () => marker.setMap(null);
    }, [ready, walkingRoute, walkingRouteOrigin, isNavigating]);

    useEffect(() => {
        if (!ready || !mapRef.current || !isNavigating || !liveLocation) return;
        const maps = window.kakao.maps;
        const { latitude, longitude, heading } = liveLocation;
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return;

        const direction = Number.isFinite(heading)
            ? `<span class="kakao-live-direction" style="transform: translateX(-50%) rotate(${heading}deg)"></span>`
            : "";
        const overlay = new maps.CustomOverlay({
            map: mapRef.current,
            position: new maps.LatLng(latitude, longitude),
            content: `<div class="kakao-live-position" aria-label="실시간 현재 위치">${direction}<span class="kakao-live-dot"></span></div>`,
            xAnchor: 0.5,
            yAnchor: 0.5,
            zIndex: 35,
        });
        return () => overlay.setMap(null);
    }, [ready, isNavigating, liveLocation]);

    useEffect(() => {
        if (!ready || !mapRef.current || !isNavigating) return;
        mapRef.current.setLevel(3);
    }, [ready, isNavigating]);

    useEffect(() => {
        if (!ready || !mapRef.current || !isNavigating || !isFollowing || !liveLocation) return;
        mapRef.current.panTo(new window.kakao.maps.LatLng(
            liveLocation.latitude, liveLocation.longitude
        ));
    }, [ready, isNavigating, isFollowing, liveLocation]);

    // 약국 목록 또는 지도 마커를 클릭하면 해당 약국으로 이동.
    useEffect(() => {
        if (!ready || !focusPharmacy || !mapRef.current) return;

        mapRef.current.panTo(
            new window.kakao.maps.LatLng(
                focusPharmacy.latitude,
                focusPharmacy.longitude
            )
        );
    }, [ready, focusPharmacy]);

    const distanceOrigin =
        mode === "nearby" ? "내 위치" : "검색 기준 위치";

    return (
        <div className="kakao-map-frame">
            <div
                ref={containerRef}
                className="kakao-map-canvas"
                aria-label="약국 위치 지도"
            />
            {ready && isNavigating && !isFollowing && (
                <button
                    type="button"
                    className="kakao-return-follow-button"
                    onClick={() => setIsFollowing(true)}
                >
                    내 위치 따라가기
                </button>
            )}
            {ready && showSearchHere && !isNavigating && (
                <button
                    type="button"
                    className="disposal-map-research-btn"
                    onClick={handleSearchHereClick}
                >
                    이 지역 재검색
                </button>
            )}

            {!ready && (
                <div className="map-overlay-state" role={error ? "alert" : "status"}>
                    {error || "카카오 지도를 불러오는 중입니다..."}
                </div>
            )}

            {ready && centerLocation && (
                <div className="kakao-radius-badge">
                    {mode === "nearby"
                        ? "내 위치"
                        : searchCenter?.name || "검색 지역"}
                    {` 기준 반경 ${radius.toLocaleString()}m`}
                </div>
            )}

            {selected && (
                <div className="map-selected-card">
                    <span className="pharmacy-number">약</span>
                    <div>
                        <strong>{selected.name}</strong>
                        <small>{selected.address}</small>
                        {selected.phone && <small>전화: {selected.phone}</small>}
                        {(mode === "nearby" || mode === "area") &&
                            selected.distanceText && (
                                <small>
                                    {distanceOrigin}에서 직선거리 {selected.distanceText}
                                </small>
                            )}
                        {walkingRoute?.status === "OK" &&
                            Number.isFinite(walkingRoute.route?.properties?.totalDistance) &&
                            Number.isFinite(walkingRoute.route?.properties?.totalTime) && (
                            <small role="status">
                                내 현재 위치에서 도보 {walkingRoute.route.properties.totalDistance.toLocaleString()}m
                                · 예상 {Math.ceil(walkingRoute.route.properties.totalTime / 60)}분
                            </small>
                        )}
                        {walkingRouteError && <small role="alert">{walkingRouteError}</small>}
                        {isNavigating && (
                            <small role="status">
                                {liveLocation
                                    ? `위치 추적 중${Number.isFinite(liveLocation.accuracy) ? ` · GPS 오차 약 ${Math.round(liveLocation.accuracy)}m` : ""}`
                                    : "실시간 GPS 위치 확인 중..."}
                            </small>
                        )}
                        {trackingError && <small role="alert">{trackingError}</small>}
                    </div>
                    <div className="map-walking-actions">
                        <button
                            type="button"
                            className="map-detail-link map-walking-button"
                            onClick={walkingRoute?.status === "OK" ? onClearWalkingRoute : onWalkingRoute}
                            disabled={walkingRouteLoading || !centerLocation}
                        >
                            {walkingRouteLoading
                                ? "경로 조회 중..."
                                : walkingRoute?.status === "OK" ? "경로 종료" : "도보 경로 보기"}
                        </button>
                        {walkingRoute?.status === "OK" && (
                            <button
                                type="button"
                                className="map-detail-link map-walking-button"
                                onClick={isNavigating ? onNavigationStop : onNavigationStart}
                            >
                                {isNavigating ? "안내 종료" : "도보 안내 시작"}
                            </button>
                        )}
                        {selected.placeUrl && (
                            <a
                                className="map-detail-link"
                                href={selected.placeUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                상세보기 ↗
                            </a>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
