package com.agro.terrainservice;

import org.springframework.boot.SpringApplication;

public class TestTerrainServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(TerrainServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
