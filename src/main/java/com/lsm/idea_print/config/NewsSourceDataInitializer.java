package com.lsm.idea_print.config;

import com.lsm.idea_print.entity.NewsSource;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2) // Run after TopicDataInitializer
@RequiredArgsConstructor
public class NewsSourceDataInitializer implements ApplicationRunner {
    
    private final NewsSourceRepository newsSourceRepository;
    private final com.lsm.idea_print.repository.ContentTopicRepository contentTopicRepository;
    
    @Override
    public void run(ApplicationArguments args) {
        initializeDefaultNewsSources();
    }
    
    private void initializeDefaultNewsSources() {
        ContentTopic aiTopic = contentTopicRepository.findByName("ai").orElse(null);
        
        // The Neuron Daily
        if (newsSourceRepository.findByNameAndIsActiveTrue("the-neuron-daily").isEmpty()) {
            NewsSource neuronDaily = NewsSource.builder()
                    .name("the-neuron-daily")
                    .baseUrl("https://www.theneurondaily.com")
                    .articleSelector("article, .post, .entry, .article-item")
                    .titleSelector("h1, h2, h3, .title, .headline, .post-title")
                    .urlSelector("a[href]")
                    .summarySelector("p, .summary, .excerpt, .description")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeoutMs(10000)
                    .contentTopic(aiTopic)
                    .isActive(true)
                    .build();
            
            newsSourceRepository.save(neuronDaily);
            log.info("Initialized news source: {}", neuronDaily.getName());
        }
        
        // AI News (example - you can add more sources)
        if (newsSourceRepository.findByNameAndIsActiveTrue("ai-news").isEmpty()) {
            NewsSource aiNews = NewsSource.builder()
                    .name("ai-news")
                    .baseUrl("https://artificialintelligence-news.com")
                    .articleSelector("article, .post-item, .news-item")
                    .titleSelector("h1, h2, .title, .entry-title")
                    .urlSelector("a[href]")
                    .summarySelector(".excerpt, .summary, p")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeoutMs(10000)
                    .contentTopic(aiTopic)
                    .isActive(true)
                    .build();
            
            newsSourceRepository.save(aiNews);
            log.info("Initialized news source: {}", aiNews.getName());
        }
        
        // VentureBeat AI
        if (newsSourceRepository.findByNameAndIsActiveTrue("venturebeat-ai").isEmpty()) {
            NewsSource ventureBeatAI = NewsSource.builder()
                    .name("venturebeat-ai")
                    .baseUrl("https://venturebeat.com/ai/")
                    .articleSelector("article, .ArticleListing")
                    .titleSelector("h1, h2, .ArticleListing__title")
                    .urlSelector("a[href]")
                    .summarySelector(".ArticleListing__excerpt, .excerpt, p")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeoutMs(15000)
                    .contentTopic(aiTopic)
                    .isActive(true)
                    .build();
            
            newsSourceRepository.save(ventureBeatAI);
            log.info("Initialized news source: {}", ventureBeatAI.getName());
        }
        
        log.info("News source initialization completed");
    }
}