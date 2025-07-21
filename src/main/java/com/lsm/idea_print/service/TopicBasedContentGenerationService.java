package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.service.interfaces.ContentGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicBasedContentGenerationService implements ContentGenerationService {
    
    private final Gpt4Service gpt4Service;
    private final AccountPromptService accountPromptService;
    private final ContentTopicRepository contentTopicRepository;
    
    @Override
    public String generateThreadsPost(NewsArticle article) {
        return generateThreadsPostAsync(article)
                .blockOptional()
                .orElse(generateFallbackContent(article));
    }
    
    @Override
    public Mono<String> generateThreadsPostAsync(NewsArticle article) {
        return generateThreadsPostForAccountAsync(article, null);
    }
    
    public Mono<String> generateThreadsPostForAccountAsync(NewsArticle article, String userId) {
        log.info("Generating topic-based Threads post for article: {} (account: {})", 
            article.getTitle(), userId);
        
        String accountPrompt = userId != null ? 
            accountPromptService.getPromptForAccount(userId) : 
            getDefaultPromptForSource(article.getSourceName());
        
        String contextualPrompt = accountPromptService.formatPromptWithArticle(
            accountPrompt,
            article.getTitle(),
            article.getSummary(),
            article.getUrl(),
            article.getSourceName()
        );
        
        return gpt4Service.generatePost(contextualPrompt)
                .filter(content -> content != null && !content.trim().isEmpty())
                .doOnNext(content -> log.info("Successfully generated topic-based content for article: {} (account: {})", 
                    article.getTitle(), userId))
                .doOnError(error -> log.error("Failed to generate topic-based content for article: {} (account: {})", 
                    article.getTitle(), userId, error))
                .onErrorReturn(generateFallbackContent(article))
                .map(String::trim);
    }
    
    public String generateThreadsPostForAccount(NewsArticle article, String userId) {
        return generateThreadsPostForAccountAsync(article, userId)
                .blockOptional()
                .orElse(generateFallbackContent(article));
    }
    
    public String generateThreadsPostForTopic(NewsArticle article, String topicName) {
        log.info("Generating content for topic: {} with article: {}", topicName, article.getTitle());
        
        String topicPrompt = contentTopicRepository.findByNameAndIsActiveTrue(topicName)
                .map(ContentTopic::getDefaultPrompt)
                .orElse(getDefaultPromptForTopic(topicName));
        
        String contextualPrompt = accountPromptService.formatPromptWithArticle(
            topicPrompt,
            article.getTitle(),
            article.getSummary(),
            article.getUrl(),
            article.getSourceName()
        );
        
        return gpt4Service.generatePost(contextualPrompt)
                .filter(content -> content != null && !content.trim().isEmpty())
                .doOnNext(content -> log.info("Successfully generated content for topic: {}", topicName))
                .doOnError(error -> log.error("Failed to generate content for topic: {}", topicName, error))
                .onErrorReturn(generateFallbackContent(article))
                .map(String::trim)
                .blockOptional()
                .orElse(generateFallbackContent(article));
    }
    
    public String generatePersonalizedContentForAccount(NewsArticle article, MetaToken account) {
        log.info("Generating personalized content for account: {} (topic: {})", 
            account.getUserId(), 
            account.getContentTopic() != null ? account.getContentTopic().getName() : "none");
        
        String prompt = determinePromptForAccount(account, article);
        
        String contextualPrompt = accountPromptService.formatPromptWithArticle(
            prompt,
            article.getTitle(),
            article.getSummary(),
            article.getUrl(),
            article.getSourceName()
        );
        
        return gpt4Service.generatePost(contextualPrompt)
                .filter(content -> content != null && !content.trim().isEmpty())
                .doOnNext(content -> log.info("Successfully generated personalized content for account: {}", 
                    account.getUserId()))
                .doOnError(error -> log.error("Failed to generate personalized content for account: {}", 
                    account.getUserId(), error))
                .onErrorReturn(generateFallbackContent(article))
                .map(String::trim)
                .blockOptional()
                .orElse(generateFallbackContent(article));
    }
    
    private String determinePromptForAccount(MetaToken account, NewsArticle article) {
        // 1. Account-specific prompt takes priority
        if (account.getPrompt() != null && !account.getPrompt().trim().isEmpty()) {
            return account.getPrompt();
        }
        
        // 2. Topic-based prompt
        if (account.getContentTopic() != null && account.getContentTopic().getDefaultPrompt() != null) {
            return account.getContentTopic().getDefaultPrompt();
        }
        
        // 3. Source-based prompt
        return getDefaultPromptForSource(article.getSourceName());
    }
    
    private String getDefaultPromptForSource(String sourceName) {
        // Return appropriate default prompt based on source
        if (sourceName != null && sourceName.toLowerCase().contains("ai")) {
            return getDefaultPromptForTopic("ai");
        }
        return getDefaultPromptForTopic("general");
    }
    
    private String getDefaultPromptForTopic(String topicName) {
        return switch (topicName.toLowerCase()) {
            case "ai" -> """
                    You are an AI technology influencer creating engaging Threads posts about AI news.
                    
                    Transform this AI news article into a casual, engaging Threads post:
                    
                    Title: %s
                    Summary: %s
                    URL: %s
                    Source: %s
                    
                    Guidelines:
                    - Keep it under 280 characters
                    - Use a casual, tech-savvy tone
                    - Include relevant AI emojis (🧠, 🤖, ⚡, 🚀, etc.)
                    - Add hashtags (#AI #MachineLearning #Tech #Innovation)
                    - Make it shareable and thought-provoking
                    - Include the URL at the end
                    
                    Create the Threads post:
                    """;
            case "life-hacks" -> """
                    You are a life-hacks influencer creating engaging Threads posts about productivity and life tips.
                    
                    Transform this content into a casual, helpful Threads post:
                    
                    Title: %s
                    Summary: %s
                    URL: %s
                    Source: %s
                    
                    Guidelines:
                    - Keep it under 280 characters
                    - Use a friendly, helpful tone
                    - Include relevant emojis (💡, ⚡, 🎯, 🔥, etc.)
                    - Add hashtags (#LifeHacks #Productivity #Tips #LifeStyle)
                    - Make it actionable and inspiring
                    - Include the URL at the end
                    
                    Create the Threads post:
                    """;
            default -> """
                    You are a content creator making engaging Threads posts.
                    
                    Transform this content into a casual, engaging Threads post:
                    
                    Title: %s
                    Summary: %s
                    URL: %s
                    Source: %s
                    
                    Guidelines:
                    - Keep it under 280 characters
                    - Use a casual, engaging tone
                    - Include relevant emojis
                    - Add appropriate hashtags
                    - Make it shareable
                    - Include the URL at the end
                    
                    Create the Threads post:
                    """;
        };
    }
    
    private String generateFallbackContent(NewsArticle article) {
        log.info("Using fallback content generation for: {}", article.getTitle());
        
        String fallback = String.format(
                "🤖 %s\n\n%s\n\n#Content #Update\n\n%s",
                article.getTitle().length() > 100 ? 
                    article.getTitle().substring(0, 97) + "..." : 
                    article.getTitle(),
                article.getSummary() != null && article.getSummary().length() > 120 ? 
                    article.getSummary().substring(0, 117) + "..." : 
                    (article.getSummary() != null ? article.getSummary() : ""),
                article.getUrl()
        );
        
        if (fallback.length() > 500) {
            fallback = String.format(
                    "🤖 %s\n\n%s",
                    article.getTitle().length() > 200 ? 
                        article.getTitle().substring(0, 197) + "..." : 
                        article.getTitle(),
                    article.getUrl()
            );
        }
        
        return fallback;
    }
}