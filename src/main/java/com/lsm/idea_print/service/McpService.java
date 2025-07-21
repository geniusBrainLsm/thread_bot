package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.service.interfaces.ContentGenerationService;
import com.lsm.idea_print.service.interfaces.NewsCrawlerService;
import com.lsm.idea_print.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {
    private final MultiSiteNewsCrawlerService multiSiteNewsCrawlerService;
    private final DuplicatePreventionService duplicatePreventionService;
    private final ContentGenerationService contentGenerationService;
    private final ThreadsPostService threadsPostService;
    private final NotificationService notificationService;
    
    public void executeFullPipeline() {
        log.info("Starting MCP (Meta Content Poster) pipeline execution");
        
        try {
            // Step 1: Crawl latest AI news from multiple sources
            Optional<NewsArticle> articleOpt = multiSiteNewsCrawlerService.crawlLatestAiNews();
            if (articleOpt.isEmpty()) {
                log.warn("No article found during crawling from any source");
                notificationService.notifyNoCrawledContent();
                return;
            }
            
            NewsArticle article = articleOpt.get();
            log.info("Crawled article: {} from source: {}", article.getTitle(), article.getSourceName());
            
            // Step 2: Check for duplicates
            if (duplicatePreventionService.isArticleAlreadyPosted(article)) {
                log.info("Article already posted, skipping: {}", article.getTitle());
                notificationService.notifyDuplicateSkipped(article.getTitle());
                return;
            }
            
            // Step 3: Post to all accounts with personalized content
            boolean postSuccess = threadsPostService.postArticleToAllAccounts(article);
            
            if (postSuccess) {
                // Step 4: Mark as posted and notify success
                duplicatePreventionService.markArticleAsPosted(article);
                notificationService.notifySuccess(article.getTitle());
                log.info("Successfully completed MCP pipeline for: {} from {}", 
                    article.getTitle(), article.getSourceName());
            } else {
                // Step 5: Notify failure
                notificationService.notifyFailure(article.getTitle(), "Failed to post to Threads");
                log.error("Failed to post to Threads for: {} from {}", 
                    article.getTitle(), article.getSourceName());
            }
            
        } catch (com.lsm.idea_print.exception.CrawlingException e) {
            log.error("Failed to crawl news content", e);
            notificationService.notifyFailure("Crawling Error", e.getMessage());
        } catch (com.lsm.idea_print.exception.ContentGenerationException e) {
            log.error("Failed to generate content", e);
            notificationService.notifyFailure("Content Generation Error", e.getMessage());
        } catch (com.lsm.idea_print.exception.PostingException e) {
            log.error("Failed to post content", e);
            notificationService.notifyFailure("Posting Error", e.getMessage());
        } catch (Exception e) {
            log.error("MCP pipeline execution failed", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            notificationService.notifyFailure("Pipeline Error", errorMessage);
            
            // Additional error handling for specific exceptions
            if (e instanceof java.net.ConnectException) {
                log.error("Network connectivity issue during MCP execution");
            } else if (e instanceof java.util.concurrent.TimeoutException) {
                log.error("Timeout occurred during MCP execution");
            } else if (e instanceof IllegalArgumentException) {
                log.error("Invalid configuration or parameters: {}", e.getMessage());
            }
        }
    }
    
    public void executeForTesting() {
        log.info("Executing MCP pipeline for testing (no duplicate prevention)");
        
        try {
            Optional<NewsArticle> articleOpt = multiSiteNewsCrawlerService.crawlLatestAiNews();
            if (articleOpt.isEmpty()) {
                log.warn("No article found during testing crawl from any source");
                return;
            }
            
            NewsArticle article = articleOpt.get();
            String generatedContent = contentGenerationService.generateThreadsPost(article);
            
            log.info("Test execution completed successfully");
            log.info("Article: {} from source: {}", article.getTitle(), article.getSourceName());
            log.info("Generated content: {}", generatedContent);
            
        } catch (Exception e) {
            log.error("MCP test execution failed", e);
        }
    }
    
    public void executeFullPipelineForAllSources() {
        log.info("Starting MCP pipeline execution for all sources");
        
        try {
            // Crawl from all active sources
            var articles = multiSiteNewsCrawlerService.crawlAllSources();
            
            if (articles.isEmpty()) {
                log.warn("No articles found from any source");
                notificationService.notifyNoCrawledContent();
                return;
            }
            
            log.info("Found {} articles from multiple sources", articles.size());
            
            for (NewsArticle article : articles) {
                try {
                    // Check for duplicates
                    if (duplicatePreventionService.isArticleAlreadyPosted(article)) {
                        log.info("Article already posted, skipping: {} from {}", 
                            article.getTitle(), article.getSourceName());
                        continue;
                    }
                    
                    // Post to all accounts with personalized content
                    boolean postSuccess = threadsPostService.postArticleToAllAccounts(article);
                    
                    if (postSuccess) {
                        duplicatePreventionService.markArticleAsPosted(article);
                        notificationService.notifySuccess(article.getTitle());
                        log.info("Successfully posted: {} from {}", 
                            article.getTitle(), article.getSourceName());
                    } else {
                        notificationService.notifyFailure(article.getTitle(), "Failed to post to Threads");
                        log.error("Failed to post: {} from {}", 
                            article.getTitle(), article.getSourceName());
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing article: {} from {}", 
                        article.getTitle(), article.getSourceName(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("MCP multi-source pipeline execution failed", e);
            notificationService.notifyFailure("Pipeline Error", e.getMessage());
        }
    }
}