package com.agro.terrainservice.mapper;

import com.agro.terrainservice.dto.TerrainResponseDTO;
import com.agro.terrainservice.entity.Terrain;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TerrainMapper {
    @Mapping(target = "geometry", expression = "java(terrain.getGeometry().toText())")
    @Mapping(target = "centroid", expression = "java(terrain.getCentroid().toText())")
    TerrainResponseDTO toDTO(Terrain terrain);
}
