package com.ucamp.app.domain.disposal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "disposal_locations")
public class DisposalLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disposal_location_id")
    private Long disposalLocationId;

    @Column(name = "location_name", nullable = false, length = 200)
    private String locationName;

    // TODO: enum 전환
    @Column(name = "location_type", nullable = false, length = 20)
    private String locationType;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Builder
    public DisposalLocation(String locationName, String locationType, String address, BigDecimal latitude,
                            BigDecimal longitude, String sourceName, LocalDateTime sourceUpdatedAt) {
        this.locationName = locationName;
        this.locationType = locationType;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sourceName = sourceName;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }
}
