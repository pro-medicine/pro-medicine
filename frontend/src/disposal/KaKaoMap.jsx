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
                                 }) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState("");

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
                    center: new maps.LatLng(37.4979, 127.0276),
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

    // 약국 마커 생성. 약국 목록이 갱신되면 이전 마커는 정리.
    useEffect(() => {
        if (!ready || !mapRef.current) return;

        const maps = window.kakao.maps;
        const map = mapRef.current;
        const markers = pharmacies.map((pharmacy) => {
            const marker = new maps.Marker({
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
    }, [ready, pharmacies, onSelect]);

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
                    </div>
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
            )}
        </div>
    );
}
