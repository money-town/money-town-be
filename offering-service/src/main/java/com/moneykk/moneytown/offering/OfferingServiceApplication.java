package com.moneykk.moneytown.offering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableFeignClients
@SpringBootApplication
public class OfferingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferingServiceApplication.class, args);
    }
}