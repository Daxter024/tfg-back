package com.agro.cropservice;

import org.springframework.boot.SpringApplication;

public class TestCropServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CropServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
