package com.agro.inputservice;

import org.springframework.boot.SpringApplication;

public class TestInputServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(InputServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
