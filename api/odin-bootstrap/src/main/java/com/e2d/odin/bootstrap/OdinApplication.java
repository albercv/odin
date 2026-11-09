package com.e2d.odin.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.e2d.odin.bootstrap", "com.e2d.odin.adapter.web"})
@EnableScheduling
public class OdinApplication {

    public static void main(String[] args) {
        SpringApplication.run(OdinApplication.class, args);
    }
}
