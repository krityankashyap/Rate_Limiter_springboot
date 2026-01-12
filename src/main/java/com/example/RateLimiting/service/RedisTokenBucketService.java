package com.example.RateLimiting.service;

import com.example.RateLimiting.config.RatelimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

// 1) Store token bucket state in redis
// 2) manage tokens per client
// 3) handle token refill based on token bucket
// 4) provide rate limiting logic
@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RatelimiterProperties ratelimiterProperties;

    private final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    // Pattern:-
    // rate_limiter:{type}:{clientId}
    // eg:- rate_limiter:tokens:123.245.67 , rate_limiter:last_refill:123.245.67

    public boolean isAllowed(String clientId) {

        String token_key = TOKENS_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);

            String tokenStr = jedis.get(token_key);  // current token count as string

            long currToken = tokenStr != null ? Long.parseLong(tokenStr) : ratelimiterProperties.getCapacity();

            if (currToken <= 0) {
                return false;
            } // currToken > 0
            long decrement = jedis.decr(token_key);
            return decrement >= 0;
        }
    }

    public long getCapacity(String clientId) {
        return ratelimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return (tokenStr != null) ? Long.parseLong(tokenStr) : ratelimiterProperties.getCapacity();
        }
    }

    public void refillTokens(String clientId, Jedis jedis) {

        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);

        if (lastRefillStr == null) {
            jedis.set(tokenKey, String.valueOf(ratelimiterProperties.getCapacity())); // for the first time initialize the bucket with full capacity
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }

        long lastRefillTime = Long.parseLong(lastRefillKey);
        long escapedTime= now- lastRefillTime;

        if(escapedTime <= 0){
            return;
        }
        // escapedTime > 0
        long tokensToAdd= (escapedTime / ratelimiterProperties.getRefillRate()) * 1000;
        if(tokensToAdd <= 0){
            return;
        }
        String tokenStr= jedis.get(tokenKey);

        long currToken = tokenStr!= null ? Long.parseLong(tokenStr) : ratelimiterProperties.getCapacity();
        long newToken = Math.min(ratelimiterProperties.getCapacity() , currToken + tokensToAdd);

        jedis.set(tokenKey , String.valueOf(newToken));
        jedis.set(lastRefillKey, String.valueOf(now));
    }
}
