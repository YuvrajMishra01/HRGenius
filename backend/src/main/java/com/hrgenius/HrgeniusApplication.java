package com.hrgenius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HrgeniusApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrgeniusApplication.class, args);
    }
}
