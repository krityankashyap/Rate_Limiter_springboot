package com.example.RateLimiting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    public final RedisTokenBucketService redisTokenBucketService;

    public boolean isAllowed(String clientId){
        return redisTokenBucketService.isAllowed(clientId);
    }

    public long getCapacity(String clientId){
        return redisTokenBucketService.getCapacity(clientId);
    }

    public long getAvailableToken(String clientId){
        return redisTokenBucketService.getAvailableTokens(clientId);
    }

}
