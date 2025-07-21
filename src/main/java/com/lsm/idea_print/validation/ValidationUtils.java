package com.lsm.idea_print.validation;

import com.lsm.idea_print.dto.NewsArticle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidationUtils {
    
    public static boolean isValidNewsArticle(NewsArticle article) {
        if (article == null) {
            log.warn("NewsArticle is null");
            return false;
        }
        
        if (article.getTitle() == null || article.getTitle().trim().isEmpty()) {
            log.warn("NewsArticle title is empty");
            return false;
        }
        
        if (article.getUrl() == null || article.getUrl().trim().isEmpty()) {
            log.warn("NewsArticle URL is empty");
            return false;
        }
        
        if (!isValidUrl(article.getUrl())) {
            log.warn("NewsArticle URL is invalid: {}", article.getUrl());
            return false;
        }
        
        return true;
    }
    
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        return url.toLowerCase().startsWith("http://") || 
               url.toLowerCase().startsWith("https://");
    }
    
    public static boolean isValidContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("Content is empty");
            return false;
        }
        
        if (content.length() > 500) {
            log.warn("Content exceeds maximum length: {} characters", content.length());
            return false;
        }
        
        return true;
    }
    
    public static boolean isValidWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return false;
        }
        
        return webhookUrl.startsWith("https://hooks.slack.com/");
    }
}