package com.lsm.idea_print.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticle {
    private String title;
    private String url;
    private String summary;
    private LocalDateTime publishedAt;
    private String sourceName;
    
    public NewsArticle(String title, String url, String summary, LocalDateTime publishedAt) {
        this.title = title;
        this.url = url;
        this.summary = summary;
        this.publishedAt = publishedAt;
    }
    
    public String generateRedisKey() {
        String sourcePrefix = sourceName != null ? sourceName + ":" : "";
        return "mcp:article:" + sourcePrefix + title.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}