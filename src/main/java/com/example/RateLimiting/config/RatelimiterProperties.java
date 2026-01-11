package com.example.RateLimiting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RatelimiterProperties {

    private long capacity= 10;
    private long refillCapacity= 5;

    private String serverURL= "http://localhost:8080";

    private int timeout= 5000;
}
