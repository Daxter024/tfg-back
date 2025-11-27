package com.agro.seasonservice;

import org.springframework.boot.SpringApplication;

public class TestSeasonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(SeasonServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
