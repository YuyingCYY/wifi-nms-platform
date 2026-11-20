package com.delta.nms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WifiNmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WifiNmsApplication.class, args);
    }
}
