package com.example.ragassistant.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        Duration ttl = cacheProperties.getRedis().getTimeToLive();
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig();
        return ttl == null ? base : base.entryTtl(ttl);
    }
}
