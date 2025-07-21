package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicBasedPostingService {
    
    private final MetaTokenRepository metaTokenRepository;
    private final ContentTopicRepository contentTopicRepository;
    private final TopicBasedContentGenerationService contentGenerationService;
    private final ThreadsPostService threadsPostService;
    
    public boolean postArticleToTopicAccounts(NewsArticle article, String topicName) {
        try {
            List<MetaToken> topicAccounts = metaTokenRepository.findByContentTopicName(topicName);
            
            if (topicAccounts.isEmpty()) {
                log.warn("No accounts found for topic: {}", topicName);
                return false;
            }
            
            log.info("Posting article to {} accounts for topic: {}", topicAccounts.size(), topicName);
            
            List<Boolean> results = Flux.fromIterable(topicAccounts)
                    .flatMap(account -> {
                        try {
                            // Generate personalized content for each account
                            String personalizedContent = contentGenerationService
                                    .generatePersonalizedContentForAccount(article, account);
                            
                            return threadsPostService.doPost(personalizedContent, 
                                    account.getUserId(), account.getAccessToken())
                                    .map(response -> {
                                        // Increment post count for successful posts
                                        account.incrementPostCount();
                                        metaTokenRepository.save(account);
                                        log.info("✅ Posted to account: {} (topic: {})", 
                                            account.getUserId(), topicName);
                                        return true;
                                    })
                                    .onErrorResume(error -> {
                                        log.error("❌ Failed to post to account: {} (topic: {}): {}", 
                                            account.getUserId(), topicName, error.getMessage());
                                        return Mono.just(false);
                                    });
                        } catch (Exception e) {
                            log.error("❌ Error generating content for account: {} (topic: {}): {}", 
                                account.getUserId(), topicName, e.getMessage());
                            return Flux.just(false);
                        }
                    })
                    .collectList()
                    .block();
            
            boolean allSuccess = results != null && results.stream().allMatch(success -> success);
            long successCount = results != null ? results.stream().mapToLong(success -> success ? 1 : 0).sum() : 0;
            
            log.info("Topic posting completed for '{}': {} successful out of {} accounts", 
                topicName, successCount, topicAccounts.size());
            
            return allSuccess;
            
        } catch (Exception e) {
            log.error("❌ Error posting article to topic '{}' accounts: {}", topicName, e.getMessage());
            return false;
        }
    }
    
    public boolean postArticleToAllTopicAccounts(NewsArticle article) {
        try {
            List<MetaToken> allAccounts = metaTokenRepository.findByActiveContentTopic();
            
            if (allAccounts.isEmpty()) {
                log.warn("No accounts with active topics found");
                return false;
            }
            
            log.info("Posting article to {} accounts across all topics", allAccounts.size());
            
            List<Boolean> results = Flux.fromIterable(allAccounts)
                    .flatMap(account -> {
                        try {
                            String personalizedContent = contentGenerationService
                                    .generatePersonalizedContentForAccount(article, account);
                            
                            String topicName = account.getContentTopic() != null ? 
                                account.getContentTopic().getName() : "general";
                            
                            return threadsPostService.doPost(personalizedContent, 
                                    account.getUserId(), account.getAccessToken())
                                    .map(response -> {
                                        account.incrementPostCount();
                                        metaTokenRepository.save(account);
                                        log.info("✅ Posted to account: {} (topic: {})", 
                                            account.getUserId(), topicName);
                                        return true;
                                    })
                                    .onErrorResume(error -> {
                                        log.error("❌ Failed to post to account: {} (topic: {}): {}", 
                                            account.getUserId(), topicName, error.getMessage());
                                        return Mono.just(false);
                                    });
                        } catch (Exception e) {
                            log.error("❌ Error with account: {}: {}", 
                                account.getUserId(), e.getMessage());
                            return Flux.just(false);
                        }
                    })
                    .collectList()
                    .block();
            
            boolean allSuccess = results != null && results.stream().allMatch(success -> success);
            long successCount = results != null ? results.stream().mapToLong(success -> success ? 1 : 0).sum() : 0;
            
            log.info("Multi-topic posting completed: {} successful out of {} accounts", 
                successCount, allAccounts.size());
            
            return allSuccess;
            
        } catch (Exception e) {
            log.error("❌ Error posting article to all topic accounts: {}", e.getMessage());
            return false;
        }
    }
    
    public Map<String, Boolean> postArticleToAllTopicsSeparately(NewsArticle article) {
        List<ContentTopic> activeTopics = contentTopicRepository.findByIsActiveTrue();
        
        return activeTopics.stream()
                .collect(java.util.stream.Collectors.toMap(
                    ContentTopic::getName,
                    topic -> {
                        try {
                            return postArticleToTopicAccounts(article, topic.getName());
                        } catch (Exception e) {
                            log.error("❌ Failed to post to topic: {}", topic.getName(), e);
                            return false;
                        }
                    }
                ));
    }
    
    public void postDifferentArticlesToEachTopic(Map<String, NewsArticle> topicArticles) {
        topicArticles.forEach((topicName, article) -> {
            try {
                boolean success = postArticleToTopicAccounts(article, topicName);
                log.info("Posted topic-specific article to '{}': {}", topicName, success ? "SUCCESS" : "FAILED");
            } catch (Exception e) {
                log.error("❌ Failed to post topic-specific article to '{}': {}", topicName, e.getMessage());
            }
        });
    }
    
    public int getAccountCountForTopic(String topicName) {
        return metaTokenRepository.findByContentTopicName(topicName).size();
    }
    
    public Map<String, Integer> getAccountCountByTopic() {
        List<ContentTopic> topics = contentTopicRepository.findByIsActiveTrue();
        
        return topics.stream()
                .collect(java.util.stream.Collectors.toMap(
                    ContentTopic::getName,
                    topic -> metaTokenRepository.findByContentTopic(topic).size()
                ));
    }
}