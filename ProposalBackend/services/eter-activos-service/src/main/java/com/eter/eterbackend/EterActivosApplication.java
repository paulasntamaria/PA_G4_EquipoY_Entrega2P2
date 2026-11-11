package com.eter.eterbackend;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class EterActivosApplication {
    public static void main(String[] args) { SpringApplication.run(EterActivosApplication.class, args); }
}
