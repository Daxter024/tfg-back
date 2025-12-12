package com.agro.terrainservice.repository;

import com.agro.terrainservice.entity.Terrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TerrainRepository extends JpaRepository<Terrain, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO terrain(
                name,
                user_id,
                geometry
            )
            VALUES (
                :name,
                :user_id,
                ST_SetSRID(ST_GeomFromGeoJSON(:geoJson), 4326)
            )
            """, nativeQuery = true)
    void saveWithCalculations(
            @Param("name") String name,
            @Param("userId") UUID user_id,
            @Param("geoJson") String geoJson);
}
