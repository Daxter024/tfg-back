package com.agro.taskservice;

import org.springframework.boot.SpringApplication;

public class TestTaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(TaskServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
