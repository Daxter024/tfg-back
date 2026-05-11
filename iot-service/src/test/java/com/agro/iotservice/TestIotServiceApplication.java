package com.agro.iotservice;

import org.springframework.boot.SpringApplication;

public class TestIotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(IotServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
