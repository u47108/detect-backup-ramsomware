package com.example.detectbackupransomware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DetectBackupRansomwareApplication {

    public static void main(String[] args) {
        SpringApplication.run(DetectBackupRansomwareApplication.class, args);
    }
}

