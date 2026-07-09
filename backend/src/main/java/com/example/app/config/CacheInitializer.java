package com.example.app.config;

import com.example.app.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CacheInitializer implements ApplicationRunner {

    private final RedisService redisService;

    public CacheInitializer(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initializeCacheAsync();
    }

    @Async
    public void initializeCacheAsync() {
        int maxRetries = 10;
        int delay = 2000;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(delay);
                redisService.refreshEquipmentSpecCache();
                log.info("Cache initialization completed successfully");
                return;
            } catch (Exception e) {
                log.warn("Cache initialization attempt {} failed: {}", i + 1, e.getMessage());
                delay *= 1.5;
            }
        }
        log.error("Cache initialization failed after {} retries", maxRetries);
    }
}