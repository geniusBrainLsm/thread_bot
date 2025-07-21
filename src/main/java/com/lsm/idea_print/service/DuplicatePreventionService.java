package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicatePreventionService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration CACHE_DURATION = Duration.ofHours(24);
    
    public boolean isArticleAlreadyPosted(NewsArticle article) {
        String key = article.generateRedisKey();
        Boolean exists = redisTemplate.hasKey(key);
        
        if (Boolean.TRUE.equals(exists)) {
            log.info("Article already posted within 24 hours: {}", article.getTitle());
            return true;
        }
        
        return false;
    }
    
    public void markArticleAsPosted(NewsArticle article) {
        String key = article.generateRedisKey();
        redisTemplate.opsForValue().set(key, article.getTitle(), CACHE_DURATION);
        log.info("Marked article as posted: {}", article.getTitle());
    }
    
    public void clearDuplicateCache() {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            });
            log.info("Cleared duplicate prevention cache");
        } catch (Exception e) {
            log.error("Failed to clear duplicate prevention cache", e);
        }
    }
}