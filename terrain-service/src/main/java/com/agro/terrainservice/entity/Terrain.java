package com.agro.terrainservice.entity;

import com.agro.terrainservice.constants.IrrigationType;
import com.agro.terrainservice.constants.SoilType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terrain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Terrain {
    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "user_id", nullable = false)
    private UUID user_id;

    @Column(nullable = false, columnDefinition = "geometry(Polygon, 4326)")
    private Polygon geometry;

    @Column(insertable = false, updatable = false, precision = 12, scale = 2)
    @Generated(GenerationTime.ALWAYS)
    private BigDecimal area_m2;

    @Column(insertable = false, updatable = false, precision = 12, scale = 2)
    @Generated(GenerationTime.ALWAYS)
    private BigDecimal perimeter_m;

    @Column(insertable = false, updatable = false, columnDefinition = "geometry(Point, 4326)")
    @Generated(GenerationTime.ALWAYS)
    private Point centroid;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "soil_type", columnDefinition = "soil_type")
    private SoilType soil_type;

    @Column(name = "slope_percent", precision = 5, scale = 2)
    private BigDecimal slope_percent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "irrigation", columnDefinition = "irrigation_type")
    private IrrigationType irrigation;

    @Column(name = "cadastral_ref", length = 40)
    private String cadastral_ref;

    @Column(nullable = false, updatable = false)
    private Instant created_at = Instant.now();

    @Column(insertable = false)
    private Instant updated_at;

}
