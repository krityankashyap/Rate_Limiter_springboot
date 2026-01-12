package com.example.RateLimiting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Data
@Component
@ConfigurationProperties(prefix = "spring-redis")
public class RedisProperties {

    private String host= "localhost";

    private int port= 6379;

    private int timeout= 2000;

    @Bean
    @ConfigurationProperties(prefix = "spring.redis")
    public  JedisPool getJedisPool(){ // java client redis library which leads java to communicate with redis server
        // JedisPool keeps multiple connections ready to reuse
        JedisPoolConfig poolConfig= new JedisPoolConfig();

        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        return new JedisPool(poolConfig, host, port, timeout);
    }
}
