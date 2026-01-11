package com.example.RateLimiting.service;

import com.example.RateLimiting.config.RatelimiterProperties;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
public class RedisTokenBucketServer {

    private final JedisPool jedisPool;
    private final RatelimiterProperties ratelimiterProperties;

    // Use static final for constants to improve memory efficiency
    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    // Constructor Injection (Lombok annotations @AllArgsConstructor were removed to ensure final fields are handled)
    public RedisTokenBucketServer(JedisPool jedisPool, RatelimiterProperties ratelimiterProperties) {
        this.jedisPool = jedisPool;
        this.ratelimiterProperties = ratelimiterProperties;
    }

    /**
     * Logic:
     * 1. Refill tokens based on time elapsed since last request.
     * 2. Check if current token count > 0.
     * 3. Decrement and allow if tokens exist.
     */
    public boolean isAllowed(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            // Step 1: Sync bucket state with current time
            refillTokens(clientId, jedis);

            // Step 2: Fetch current state after refill
            String tokenStr = jedis.get(tokenKey);
            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : 0;

            if (currentTokens <= 0) {
                return false; // Rate limit exceeded
            }

            // Step 3: Consume one token
            jedis.decr(tokenKey);
            return true;
        }
    }

    /**
     * Logic:
     * Calculates tokens earned based on time gap (ms) and adds them to the bucket,
     * ensuring we don't exceed the defined max capacity.
     */
    public void refillTokens(String clientId, Jedis jedis) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);

        // Case: New client or first request in a long time
        if (lastRefillStr == null) {
            jedis.set(tokenKey, String.valueOf(ratelimiterProperties.getCapacity()));
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }

        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime; // milliseconds

        if (elapsedTime <= 0) return;

        // tokensToAdd = (time_in_sec) * (tokens_per_sec)
        long tokensToAdd = (elapsedTime * ratelimiterProperties.getRefillCapacity()) / 1000;

        if (tokensToAdd > 0) {
            // Fetch current tokens to perform addition
            String currentTokenStr = jedis.get(tokenKey);
            long currentToken = currentTokenStr != null ? Long.parseLong(currentTokenStr) : 0;

            // newTokens = current + added, but never more than max capacity
            long newTokens = Math.min(ratelimiterProperties.getCapacity(), currentToken + tokensToAdd);

            jedis.set(tokenKey, String.valueOf(newTokens));

            // IMPORTANT: Only update timestamp when tokens are actually added
            // to avoid losing partial tokens between very fast requests.
            jedis.set(lastRefillKey, String.valueOf(now));
        }
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Long.parseLong(tokenStr) : ratelimiterProperties.getCapacity();
        }
    }
}