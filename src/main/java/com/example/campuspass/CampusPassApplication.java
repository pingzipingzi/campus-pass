package com.example.campuspass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CampusPassApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusPassApplication.class, args);
    }

}
