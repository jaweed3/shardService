package com.example.shardService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardServiceApplication.class, args);
    }

}
