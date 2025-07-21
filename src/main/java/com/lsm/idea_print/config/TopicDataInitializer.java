package com.lsm.idea_print.config;

import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.entity.NewsSource;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1) // Run before NewsSourceDataInitializer
@RequiredArgsConstructor
public class TopicDataInitializer implements ApplicationRunner {
    
    private final ContentTopicRepository contentTopicRepository;
    private final NewsSourceRepository newsSourceRepository;
    
    @Override
    public void run(ApplicationArguments args) {
        initializeDefaultTopics();
        updateNewsSourcesWithTopics();
    }
    
    private void initializeDefaultTopics() {
        // AI Topic
        if (contentTopicRepository.findByName("ai").isEmpty()) {
            ContentTopic aiTopic = ContentTopic.builder()
                    .name("ai")
                    .displayName("Artificial Intelligence")
                    .description("AI news, machine learning, and technology innovations")
                    .defaultPrompt("""
                            You are an AI technology influencer creating engaging Threads posts about AI news.
                            
                            Transform this AI news article into a casual, engaging Threads post:
                            
                            Title: %s
                            Summary: %s
                            URL: %s
                            Source: %s
                            
                            Guidelines:
                            - Keep it under 450 characters
                            - Use a casual, tech-savvy tone
                            - Include relevant AI emojis (🧠, 🤖, ⚡, 🚀, etc.)
                            - Add hashtags (#AI #MachineLearning #Tech #Innovation)
                            - Make it shareable and thought-provoking
                            - writting to korean
                            
                            Create the Threads post:
                            """)
                    .isActive(true)
                    .build();
            
            contentTopicRepository.save(aiTopic);
            log.info("Initialized content topic: AI");
        }
        
        // Life Hacks Topic (for future use)
        if (contentTopicRepository.findByName("life-hacks").isEmpty()) {
            ContentTopic lifeHacksTopic = ContentTopic.builder()
                    .name("life-hacks")
                    .displayName("Life Hacks & Productivity")
                    .description("Productivity tips, life hacks, and lifestyle improvements")
                    .defaultPrompt("""
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
                            """)
                    .isActive(false) // Not active yet, no sources configured
                    .build();
            
            contentTopicRepository.save(lifeHacksTopic);
            log.info("Initialized content topic: Life Hacks (inactive)");
        }
        
        // General Topic (fallback)
        if (contentTopicRepository.findByName("general").isEmpty()) {
            ContentTopic generalTopic = ContentTopic.builder()
                    .name("general")
                    .displayName("General Content")
                    .description("General content and news")
                    .defaultPrompt("""
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
                            """)
                    .isActive(true)
                    .build();
            
            contentTopicRepository.save(generalTopic);
            log.info("Initialized content topic: General");
        }
        
        log.info("Topic initialization completed");
    }
    
    private void updateNewsSourcesWithTopics() {
        // Assign AI topic to existing AI-related news sources
        ContentTopic aiTopic = contentTopicRepository.findByName("ai").orElse(null);
        
        if (aiTopic != null) {
            // Update existing sources that don't have a topic assigned
            newsSourceRepository.findAll().stream()
                    .filter(source -> source.getContentTopic() == null)
                    .filter(source -> isAiRelatedSource(source.getName()))
                    .forEach(source -> {
                        source.setContentTopic(aiTopic);
                        newsSourceRepository.save(source);
                        log.info("Assigned AI topic to existing source: {}", source.getName());
                    });
        }
        
        log.info("News source topic assignment completed");
    }
    
    private boolean isAiRelatedSource(String sourceName) {
        if (sourceName == null) return false;
        
        String lowerName = sourceName.toLowerCase();
        return lowerName.contains("ai") || 
               lowerName.contains("neuron") || 
               lowerName.contains("artificial") ||
               lowerName.contains("machine") ||
               lowerName.contains("tech");
    }
}