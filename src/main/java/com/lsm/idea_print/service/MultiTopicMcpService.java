package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTopicMcpService {
    
    private final TopicBasedCrawlerService topicBasedCrawlerService;
    private final DuplicatePreventionService duplicatePreventionService;
    private final TopicBasedPostingService topicBasedPostingService;
    private final NotificationService notificationService;
    private final ContentTopicRepository contentTopicRepository;
    
    public void executeFullPipelineForAllTopics() {
        log.info("Starting Multi-Topic MCP pipeline execution for all topics");
        
        try {
            List<ContentTopic> activeTopics = contentTopicRepository.findByIsActiveTrue();
            
            if (activeTopics.isEmpty()) {
                log.warn("No active topics found");
                notificationService.notifyNoCrawledContent();
                return;
            }
            
            log.info("Processing {} active topics", activeTopics.size());
            
            int successfulTopics = 0;
            
            for (ContentTopic topic : activeTopics) {
                try {
                    boolean topicSuccess = executeTopicPipeline(topic.getName());
                    if (topicSuccess) {
                        successfulTopics++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process topic: {}", topic.getName(), e);
                }
            }
            
            log.info("Multi-topic pipeline completed: {}/{} topics processed successfully", 
                successfulTopics, activeTopics.size());
            
            if (successfulTopics > 0) {
                notificationService.notifySuccess(String.format("Multi-Topic Pipeline: %d/%d topics successful", 
                    successfulTopics, activeTopics.size()));
            } else {
                notificationService.notifyFailure("Multi-Topic Pipeline", "No topics processed successfully");
            }
            
        } catch (Exception e) {
            log.error("Multi-topic MCP pipeline execution failed", e);
            notificationService.notifyFailure("Multi-Topic Pipeline Error", e.getMessage());
        }
    }
    
    public boolean executeTopicPipeline(String topicName) {
        log.info("Starting MCP pipeline for topic: {}", topicName);
        
        try {
            // Step 1: Crawl latest news for this topic
            Optional<NewsArticle> articleOpt = topicBasedCrawlerService.crawlLatestNewsForTopic(topicName);
            
            if (articleOpt.isEmpty()) {
                log.warn("No article found for topic: {}", topicName);
                return false;
            }
            
            NewsArticle article = articleOpt.get();
            log.info("Crawled article for topic '{}': {} from {}", 
                topicName, article.getTitle(), article.getSourceName());
            
            // Step 2: Check for duplicates
            if (duplicatePreventionService.isArticleAlreadyPosted(article)) {
                log.info("Article already posted, skipping: {} (topic: {})", article.getTitle(), topicName);
                return false; // Not a failure, just already processed
            }
            
            // Step 3: Post to topic-specific accounts
            boolean postSuccess = topicBasedPostingService.postArticleToTopicAccounts(article, topicName);
            
            if (postSuccess) {
                // Step 4: Mark as posted
                duplicatePreventionService.markArticleAsPosted(article);
                log.info("Successfully completed MCP pipeline for topic '{}': {}", topicName, article.getTitle());
                return true;
            } else {
                log.error("Failed to post to topic '{}' accounts: {}", topicName, article.getTitle());
                return false;
            }
            
        } catch (Exception e) {
            log.error("MCP pipeline failed for topic: {}", topicName, e);
            return false;
        }
    }
    
    public void executeCrossTopicPipeline() {
        log.info("Starting Cross-Topic MCP pipeline execution");
        
        try {
            // Step 1: Crawl articles from all topics
            Map<String, List<NewsArticle>> topicArticles = topicBasedCrawlerService.crawlAllTopics();
            
            if (topicArticles.isEmpty()) {
                log.warn("No articles found from any topic");
                notificationService.notifyNoCrawledContent();
                return;
            }
            
            log.info("Found articles for {} topics", topicArticles.size());
            
            // Step 2: Process each topic's articles
            for (Map.Entry<String, List<NewsArticle>> entry : topicArticles.entrySet()) {
                String topicName = entry.getKey();
                List<NewsArticle> articles = entry.getValue();
                
                log.info("Processing {} articles for topic: {}", articles.size(), topicName);
                
                for (NewsArticle article : articles) {
                    try {
                        // Check for duplicates
                        if (duplicatePreventionService.isArticleAlreadyPosted(article)) {
                            log.info("Article already posted, skipping: {} (topic: {})", 
                                article.getTitle(), topicName);
                            continue;
                        }
                        
                        // Post to topic accounts
                        boolean success = topicBasedPostingService.postArticleToTopicAccounts(article, topicName);
                        
                        if (success) {
                            duplicatePreventionService.markArticleAsPosted(article);
                            log.info("✅ Posted article for topic '{}': {}", topicName, article.getTitle());
                        } else {
                            log.error("❌ Failed to post article for topic '{}': {}", topicName, article.getTitle());
                        }
                        
                    } catch (Exception e) {
                        log.error("Error processing article for topic '{}': {}", topicName, e.getMessage());
                    }
                }
            }
            
            notificationService.notifySuccess("Cross-Topic Pipeline completed for " + topicArticles.size() + " topics");
            
        } catch (Exception e) {
            log.error("Cross-topic MCP pipeline execution failed", e);
            notificationService.notifyFailure("Cross-Topic Pipeline Error", e.getMessage());
        }
    }
    
    public void executeUniversalPipeline() {
        log.info("Starting Universal MCP pipeline (single article to all topics)");
        
        try {
            // Step 1: Get the latest AI article (primary topic)
            Optional<NewsArticle> articleOpt = topicBasedCrawlerService.crawlLatestNewsForTopic("ai");
            
            if (articleOpt.isEmpty()) {
                log.warn("No article found for universal posting");
                notificationService.notifyNoCrawledContent();
                return;
            }
            
            NewsArticle article = articleOpt.get();
            log.info("Universal article selected: {} from {}", article.getTitle(), article.getSourceName());
            
            // Step 2: Check for duplicates
            if (duplicatePreventionService.isArticleAlreadyPosted(article)) {
                log.info("Article already posted universally, skipping: {}", article.getTitle());
                notificationService.notifyDuplicateSkipped(article.getTitle());
                return;
            }
            
            // Step 3: Post to all topic accounts with personalized content
            boolean postSuccess = topicBasedPostingService.postArticleToAllTopicAccounts(article);
            
            if (postSuccess) {
                duplicatePreventionService.markArticleAsPosted(article);
                notificationService.notifySuccess("Universal: " + article.getTitle());
                log.info("Successfully completed universal MCP pipeline: {}", article.getTitle());
            } else {
                notificationService.notifyFailure("Universal Pipeline", "Failed to post to all accounts");
                log.error("Failed universal posting: {}", article.getTitle());
            }
            
        } catch (Exception e) {
            log.error("Universal MCP pipeline execution failed", e);
            notificationService.notifyFailure("Universal Pipeline Error", e.getMessage());
        }
    }
    
    public Map<String, Integer> getTopicAccountStats() {
        return topicBasedPostingService.getAccountCountByTopic();
    }
    
    public void executeTestingPipeline(String topicName) {
        log.info("Executing testing pipeline for topic: {}", topicName);
        
        try {
            Optional<NewsArticle> articleOpt = topicBasedCrawlerService.crawlLatestNewsForTopic(topicName);
            
            if (articleOpt.isEmpty()) {
                log.warn("No article found during testing for topic: {}", topicName);
                return;
            }
            
            NewsArticle article = articleOpt.get();
            
            log.info("Test execution completed for topic: {}", topicName);
            log.info("Article: {} from source: {}", article.getTitle(), article.getSourceName());
            
            int accountCount = topicBasedPostingService.getAccountCountForTopic(topicName);
            log.info("Would post to {} accounts for topic: {}", accountCount, topicName);
            
        } catch (Exception e) {
            log.error("MCP test execution failed for topic: {}", topicName, e);
        }
    }
}