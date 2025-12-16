package com.agro.terrainservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "grpc.server.port=0")
class TerrainServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
