import { useEffect, useRef, useState } from "react";

const JAVASCRIPT_KEY = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY;
let sdkPromise = null;

function loadKakaoMap() {
    if (!JAVASCRIPT_KEY) {
        return Promise.reject(new Error("VITE_KAKAO_JAVASCRIPT_KEY가 설정되지 않았습니다."));
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
                                     pharmacies,
                                     selected,
                                     onSelect,
                                     mode,
                                     userLocation,
                                     radius,
                                     focusPharmacy,
                                 }) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState("");

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
        const markers = pharmacies.map((pharmacy) => {
            const marker = new maps.Marker({
                map: mapRef.current,
                position: new maps.LatLng(pharmacy.latitude, pharmacy.longitude),
                title: pharmacy.name,
                clickable: true,
            });
            maps.event.addListener(marker, "click", () => onSelect(pharmacy));
            return marker;
        });

        return () => {
            markers.forEach((marker) => marker.setMap(null));
        };
    }, [ready, pharmacies, onSelect]);

    useEffect(() => {
        if (!ready || !mapRef.current || mode !== "keyword" || !pharmacies.length) return;

        const pharmacy = pharmacies[0];
        const maps = window.kakao.maps;
        mapRef.current.setLevel(4);
        mapRef.current.setCenter(new maps.LatLng(pharmacy.latitude, pharmacy.longitude));
    }, [ready, mode, pharmacies]);

    useEffect(() => {
        if (!ready || !mapRef.current || mode !== "nearby" || !userLocation) return;

        const maps = window.kakao.maps;
        const map = mapRef.current;
        const center = new maps.LatLng(userLocation.latitude, userLocation.longitude);
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

        const myLocationMarker = new maps.CustomOverlay({
            map,
            position: center,
            content: '<div class="kakao-my-location" aria-label="내 현재 위치"><span></span></div>',
            yAnchor: 0.5,
            xAnchor: 0.5,
            zIndex: 5,
        });

        const latitudeDelta = radius / 111320;
        const longitudeDelta = radius /
            (111320 * Math.max(Math.abs(Math.cos(userLocation.latitude * Math.PI / 180)), 0.01));
        const bounds = new maps.LatLngBounds(
            new maps.LatLng(userLocation.latitude - latitudeDelta, userLocation.longitude - longitudeDelta),
            new maps.LatLng(userLocation.latitude + latitudeDelta, userLocation.longitude + longitudeDelta)
        );
        map.setBounds(bounds, 35, 30, 35, 30);

        return () => {
            circle.setMap(null);
            myLocationMarker.setMap(null);
        };
    }, [ready, mode, userLocation, radius]);

    useEffect(() => {
        if (!ready || !focusPharmacy || !mapRef.current) return;
        const center = new window.kakao.maps.LatLng(
            focusPharmacy.latitude,
            focusPharmacy.longitude
        );
        mapRef.current.panTo(center);
    }, [ready, focusPharmacy]);

    return (
        <div className="kakao-map-frame">
            <div ref={containerRef} className="kakao-map-canvas" aria-label="약국 위치 지도" />

            {!ready && (
                <div className="map-overlay-state" role={error ? "alert" : "status"}>
                    {error || "카카오 지도를 불러오는 중입니다..."}
                </div>
            )}

            {mode === "nearby" && userLocation && ready && (
                <div className="kakao-radius-badge">내 위치 기준 반경 {radius.toLocaleString()}m</div>
            )}

            {selected && (
                <div className="map-selected-card">
                    <span className="pharmacy-number">약</span>
                    <div>
                        <strong>{selected.name}</strong>
                        <small>{selected.address}</small>
                        {selected.phone && <small>전화: {selected.phone}</small>}
                        {mode === "nearby" && selected.distanceText && (
                            <small>내 위치에서 직선거리 {selected.distanceText}</small>
                        )}
                    </div>
                    {selected.placeUrl && (
                        <a className="map-detail-link" href={selected.placeUrl} target="_blank" rel="noopener noreferrer">
                            상세보기 ↗
                        </a>
                    )}
                </div>
            )}
        </div>
    );
}
