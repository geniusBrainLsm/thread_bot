package com.lsm.idea_print.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "news_source")
public class NewsSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String baseUrl;
    
    @Column(name = "article_selector")
    private String articleSelector;
    
    @Column(name = "title_selector")
    private String titleSelector;
    
    @Column(name = "url_selector")
    private String urlSelector;
    
    @Column(name = "summary_selector")
    private String summarySelector;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Builder.Default
    @Column(name = "timeout_ms")
    private Integer timeoutMs = 10000;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_topic_id")
    private ContentTopic contentTopic;
    
    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}