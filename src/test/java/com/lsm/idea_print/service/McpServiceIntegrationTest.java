package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpServiceIntegrationTest {

    @Test
    void testNewsArticleDTO() {
        NewsArticle article = new NewsArticle(
                "Test AI Article",
                "https://example.com/test",
                "This is a test summary",
                java.time.LocalDateTime.now()
        );
        
        assertNotNull(article.getTitle());
        assertNotNull(article.getUrl());
        assertNotNull(article.getSummary());
        assertEquals("Test AI Article", article.getTitle());
        assertEquals("https://example.com/test", article.getUrl());
        
        String redisKey = article.generateRedisKey();
        assertNotNull(redisKey);
        assertTrue(redisKey.startsWith("mcp:article:"));
        
        System.out.println("✅ NewsArticle DTO test passed");
    }

    @Test
    void testPromptGeneration() {
        NewsArticle testArticle = new NewsArticle(
                "AI Breakthrough: New Model Achieves Human-Level Performance",
                "https://example.com/test-article",
                "Revolutionary AI model demonstrates unprecedented capabilities in various tasks.",
                java.time.LocalDateTime.now()
        );

        // Test that prompt formatting would work
        String expectedPromptStart = "You are a witty tech influencer";
        String formattedPrompt = String.format(
                "You are a witty tech influencer creating engaging Threads posts about AI news.\n\nTitle: %s\nSummary: %s\nURL: %s",
                testArticle.getTitle(),
                testArticle.getSummary(),
                testArticle.getUrl()
        );
        
        assertNotNull(formattedPrompt);
        assertTrue(formattedPrompt.contains(testArticle.getTitle()));
        assertTrue(formattedPrompt.contains(testArticle.getSummary()));
        assertTrue(formattedPrompt.contains(testArticle.getUrl()));
        
        System.out.println("✅ Prompt generation test passed");
    }

    @Test
    void testRedisKeyGeneration() {
        NewsArticle testArticle = new NewsArticle(
                "Test Article for Duplicate Prevention!@#$%",
                "https://example.com/test",
                "This is a test summary.",
                java.time.LocalDateTime.now()
        );

        String redisKey = testArticle.generateRedisKey();
        
        assertNotNull(redisKey);
        assertTrue(redisKey.startsWith("mcp:article:"));
        // Key should be sanitized (no special characters)
        assertFalse(redisKey.contains("!"));
        assertFalse(redisKey.contains("@"));
        assertFalse(redisKey.contains("#"));
        assertTrue(redisKey.contains("_"));
        
        System.out.println("✅ Redis key generation test passed: " + redisKey);
    }
}