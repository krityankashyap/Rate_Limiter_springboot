package com.example.RateLimiting.service;

import com.example.RateLimiting.config.RatelimiterProperties;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@NoArgsConstructor
@AllArgsConstructor

// store token bucket state in redis
// manage tokens per client
// handle token refill based on time
// provide rate limiting logic

public class RedisTokenBucketServer {

    private final JedisPool jedisPool;
    private final RatelimiterProperties ratelimiterProperties;

    private final String TOKENS_KEY_PREFIX= "rate_limiter:tokens:";

    private final String LAST_REFILL_KEY_PREFIX= "rate-limiter:last_refill:";


    public RedisTokenBucketServer(JedisPool jedisPool, RatelimiterProperties ratelimiterProperties) {
        this.jedisPool = jedisPool;
        this.ratelimiterProperties = ratelimiterProperties;
    }

   // pattern to store details
    // rate_limiter:{type}:{clientId}
    //eg:-
    // rate_limiter:tokens:192.168.1.100 -> current token count
    // rate_limiter:last_refill:192.168.1.100 -> last refill timestamp

    public boolean isAllowed(String clientId){
        String tokenkey= TOKENS_KEY_PREFIX + clientId;

        try (Jedis jedis= jedisPool.getResource()){  // we get the connection to JedisPool
            refillTokens(clientId, jedis);           // we refill the bucket

            String tokenStr= jedis.get(tokenkey);  // current number of tokens as string

            long currentTokens= tokenStr!= null ? Long.parseLong(tokenStr) : ratelimiterProperties.getCapacity();

            if(currentTokens <= 0){
                return false;
            }

            // currentTokens> 0 then we have to decrement the token per request
            long decrement= jedis.decr(tokenkey);
            return decrement>= 0;

        }
    }


}
