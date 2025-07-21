# Multi-Topic MCP Bot Architecture Documentation

## Overview
This document describes the architecture and execution flow of the Multi-Topic Meta Content Poster (MCP) bot system. The system is designed to crawl multiple AI newsletter sites, generate personalized content for different account types, and post to Meta Threads accounts based on content topics.

## System Architecture

### Core Design Principles
- **Multi-Topic Support**: Accounts are organized by content topics (AI, life-hacks, etc.)
- **Personalization**: Each account can have custom prompts or use topic defaults
- **Scalability**: Easy to add new topics and news sources
- **Separation of Concerns**: Clean layer separation between crawling, generation, and posting

## Entity Layer

### 1. ContentTopic Entity
**File**: `src/main/java/com/lsm/idea_print/entity/ContentTopic.java`

```java
@Entity
@Table(name = "content_topic")
public class ContentTopic {
    private Long id;
    private String name;              // Unique identifier (e.g., "ai", "life-hacks")
    private String displayName;       // Human-readable name
    private String description;       // Topic description
    private String defaultPrompt;     // Default content generation prompt
    private Boolean isActive;         // Whether topic is active
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Purpose**: Defines content categories and their default behavior
**Relationships**: One-to-Many with NewsSource and MetaToken

### 2. Enhanced MetaToken Entity
**File**: `src/main/java/com/lsm/idea_print/entity/MetaToken.java`

```java
@Entity
@Table(name = "meta_token")
public class MetaToken {
    private Long id;
    private String userId;            // Meta Threads user ID
    private String accessToken;       // Meta API access token
    private String prompt;            // Account-specific prompt (optional)
    private ContentTopic contentTopic; // Associated topic
    private String accountName;       // Account display name
    private String accountDescription; // Account description
    private Integer postCount;        // Number of posts made
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Purpose**: Represents Meta Threads accounts with topic assignment
**Key Features**: 
- Can override topic default prompt with custom prompt
- Tracks posting statistics
- Linked to specific content topics

### 3. Enhanced NewsSource Entity
**File**: `src/main/java/com/lsm/idea_print/entity/NewsSource.java`

```java
@Entity
@Table(name = "news_source")
public class NewsSource {
    private Long id;
    private String name;              // Source identifier
    private String baseUrl;           // Website URL
    private ContentTopic contentTopic; // Associated topic
    private String articleSelector;   // CSS selector for articles
    private String titleSelector;     // CSS selector for titles
    private String urlSelector;       // CSS selector for URLs
    private String summarySelector;   // CSS selector for summaries
    private String userAgent;         // HTTP user agent
    private Integer timeoutMs;        // Request timeout
    private Boolean isActive;         // Whether source is active
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Purpose**: Defines news sources and their crawling configuration
**Key Features**: Topic-specific sources for targeted content crawling

### 4. NewsArticle DTO
**File**: `src/main/java/com/lsm/idea_print/dto/NewsArticle.java`

```java
public class NewsArticle {
    private String title;
    private String url;
    private String summary;
    private LocalDateTime publishedAt;
    private String sourceName;       // Source identifier with topic prefix
    
    public String generateRedisKey() {
        String sourcePrefix = sourceName != null ? sourceName + ":" : "";
        return "mcp:article:" + sourcePrefix + title.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}
```

**Purpose**: Data transfer object for crawled articles with source tracking

## Repository Layer

### 1. ContentTopicRepository
**File**: `src/main/java/com/lsm/idea_print/repository/ContentTopicRepository.java`

```java
public interface ContentTopicRepository extends JpaRepository<ContentTopic, Long> {
    Optional<ContentTopic> findByNameAndIsActiveTrue(String name);
    List<ContentTopic> findByIsActiveTrue();
    Optional<ContentTopic> findByName(String name);
}
```

**Purpose**: Data access for content topics

### 2. Enhanced MetaTokenRepository
**File**: `src/main/java/com/lsm/idea_print/repository/MetaTokenRepository.java`

```java
public interface MetaTokenRepository extends JpaRepository<MetaToken, Long> {
    Optional<MetaToken> findByUserId(String userId);
    List<MetaToken> findByContentTopic(ContentTopic contentTopic);
    List<MetaToken> findByContentTopicName(String topicName);
    
    @Query("SELECT m FROM MetaToken m WHERE m.contentTopic.isActive = true")
    List<MetaToken> findByActiveContentTopic();
    
    // Statistics methods
    List<MetaToken> findByPostCountLessThan(Integer postCount);
    List<MetaToken> findByPostCountGreaterThanEqual(Integer postCount);
    @Query("SELECT SUM(m.postCount) FROM MetaToken m")
    Long getTotalPostCount();
    List<MetaToken> findAllByOrderByPostCountDesc();
    boolean existsByUserId(String userId);
}
```

**Purpose**: Data access for Meta Threads accounts with topic-based queries

### 3. Enhanced NewsSourceRepository
**File**: `src/main/java/com/lsm/idea_print/repository/NewsSourceRepository.java`

```java
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByIsActiveTrue();
    Optional<NewsSource> findByNameAndIsActiveTrue(String name);
    List<NewsSource> findByContentTopicAndIsActiveTrue(ContentTopic contentTopic);
    List<NewsSource> findByContentTopicNameAndIsActiveTrue(String topicName);
}
```

**Purpose**: Data access for news sources with topic-based filtering

## Service Layer

### 1. TopicBasedContentGenerationService
**File**: `src/main/java/com/lsm/idea_print/service/TopicBasedContentGenerationService.java`

**Purpose**: Generates personalized content based on account and topic preferences

**Key Methods**:
```java
// Standard content generation
public String generateThreadsPost(NewsArticle article)
public Mono<String> generateThreadsPostAsync(NewsArticle article)

// Account-specific generation
public String generateThreadsPostForAccount(NewsArticle article, String userId)
public Mono<String> generateThreadsPostForAccountAsync(NewsArticle article, String userId)

// Topic-specific generation
public String generateThreadsPostForTopic(NewsArticle article, String topicName)

// Personalized generation with full account context
public String generatePersonalizedContentForAccount(NewsArticle article, MetaToken account)
```

**Prompt Resolution Logic**:
1. Account-specific prompt (highest priority)
2. Topic-based default prompt
3. Source-based prompt (fallback)
4. General prompt (final fallback)

**Topic-Specific Prompts**:
- **AI**: Tech-savvy tone with AI emojis and hashtags
- **Life-Hacks**: Helpful tone with productivity emojis
- **General**: Balanced approach for miscellaneous content

### 2. TopicBasedCrawlerService
**File**: `src/main/java/com/lsm/idea_print/service/TopicBasedCrawlerService.java`

**Purpose**: Crawls news from topic-specific sources

**Key Methods**:
```java
// Legacy compatibility
public Optional<NewsArticle> crawlLatestAiNews()

// Topic-specific crawling
public Optional<NewsArticle> crawlLatestNewsForTopic(String topicName)
public List<NewsArticle> crawlAllSourcesForTopic(String topicName)

// Multi-topic crawling
public Map<String, List<NewsArticle>> crawlAllTopics()
public Optional<NewsArticle> crawlFromTopicSource(ContentTopic topic)
```

**Execution Flow**:
1. Get active sources for topic
2. Try each source until article found
3. Extract title, URL, summary using configured selectors
4. Validate article content
5. Return article with source attribution

### 3. TopicBasedPostingService
**File**: `src/main/java/com/lsm/idea_print/service/TopicBasedPostingService.java`

**Purpose**: Posts content to accounts grouped by topic

**Key Methods**:
```java
// Topic-specific posting
public boolean postArticleToTopicAccounts(NewsArticle article, String topicName)

// Multi-topic posting
public boolean postArticleToAllTopicAccounts(NewsArticle article)
public Map<String, Boolean> postArticleToAllTopicsSeparately(NewsArticle article)

// Specialized posting
public void postDifferentArticlesToEachTopic(Map<String, NewsArticle> topicArticles)

// Statistics
public int getAccountCountForTopic(String topicName)
public Map<String, Integer> getAccountCountByTopic()
```

**Posting Flow**:
1. Get accounts for target topic(s)
2. Generate personalized content for each account
3. Post to Threads API using ThreadsPostService
4. Update post counts for successful posts
5. Return success status

### 4. MultiTopicMcpService
**File**: `src/main/java/com/lsm/idea_print/service/MultiTopicMcpService.java`

**Purpose**: Orchestrates multi-topic pipeline execution

**Pipeline Types**:

#### Full Pipeline for All Topics
```java
public void executeFullPipelineForAllTopics()
```
- Processes each active topic separately
- Independent execution per topic
- Comprehensive error handling

#### Topic-Specific Pipeline
```java
public boolean executeTopicPipeline(String topicName)
```
- Crawl latest news for specific topic
- Check for duplicates
- Post to topic accounts
- Mark as processed

#### Cross-Topic Pipeline
```java
public void executeCrossTopicPipeline()
```
- Crawl articles from all topics
- Process each topic's articles independently
- Topic-specific content distribution

#### Universal Pipeline
```java
public void executeUniversalPipeline()
```
- Get single article (from AI topic)
- Generate personalized content for all accounts
- Cross-topic content sharing

#### Testing Pipeline
```java
public void executeTestingPipeline(String topicName)
```
- Dry-run without posting
- Content generation testing
- Account count verification

### 5. AccountPromptService
**File**: `src/main/java/com/lsm/idea_print/service/AccountPromptService.java`

**Purpose**: Manages account-specific and topic-based prompts

**Key Methods**:
```java
public String getPromptForAccount(String userId)
public Map<String, String> getAllAccountPrompts()
public boolean updatePromptForAccount(String userId, String newPrompt)
public String formatPromptWithArticle(String prompt, String title, String summary, String url, String sourceName)
```

**Prompt Resolution**:
1. Check for account-specific prompt
2. Fall back to topic default prompt
3. Use system default if no topic assigned

### 6. Enhanced ThreadsPostService
**File**: `src/main/java/com/lsm/idea_print/service/ThreadsPostService.java`

**Purpose**: Handles Meta Threads API interactions

**Key Enhancements**:
```java
// Legacy method (unchanged)
public boolean postToAllAccounts(String content)

// New personalized posting
public boolean postArticleToAllAccounts(NewsArticle article)
```

**Enhanced Features**:
- Integration with TopicBasedContentGenerationService
- Account-specific content generation
- Automatic post count tracking
- Better error handling and logging

### 7. DuplicatePreventionService
**File**: `src/main/java/com/lsm/idea_print/service/DuplicatePreventionService.java`

**Purpose**: Prevents duplicate article posting using Redis

**Enhanced Key Generation**:
- Includes source name for better uniqueness
- Topic-aware duplicate detection
- 24-hour cache duration

## Scheduling Layer

### ScheduleService
**File**: `src/main/java/com/lsm/idea_print/service/ScheduleService.java`

**Purpose**: Orchestrates scheduled pipeline execution

**Schedule Overview**:
```java
@Scheduled(cron = "0 0 9 * * *")      // 9:00 AM - Legacy MCP Pipeline
public void executeMcpPipeline()

@Scheduled(cron = "0 30 10 * * *")    // 10:30 AM - Multi-Topic Pipeline
public void executeMultiTopicPipeline()

@Scheduled(cron = "0 0 11 * * *")     // 11:00 AM - AI Topic Pipeline
public void executeAiTopicPipeline()

@Scheduled(cron = "0 0 12 * * *")     // 12:00 PM - All Sources Pipeline
public void executeMcpPipelineAllSources()

@Scheduled(cron = "0 0 14 * * *")     // 2:00 PM - Cross-Topic Pipeline
public void executeCrossTopicPipeline()

@Scheduled(cron = "0 0 16 * * *")     // 4:00 PM - Universal Pipeline
public void executeUniversalPipeline()

@Scheduled(cron = "0 0 */3 * * *")    // Every 3 hours - Legacy posting
public void postDailyGptContent()

// Commented out until life-hacks sources are added:
// @Scheduled(cron = "0 0 15 * * *")  // 3:00 PM - Life Hacks Pipeline
// public void executeLifeHacksTopicPipeline()
```

## Configuration Layer

### 1. TopicDataInitializer
**File**: `src/main/java/com/lsm/idea_print/config/TopicDataInitializer.java`

**Purpose**: Initializes default content topics on application startup

**Execution Order**: @Order(1) - Runs first

**Initialized Topics**:
- **AI Topic**: Active, with AI-focused prompt template
- **Life-Hacks Topic**: Inactive, ready for future activation
- **General Topic**: Active, fallback for unassigned content

**Topic Assignment**: Automatically assigns existing AI-related sources to AI topic

### 2. Enhanced NewsSourceDataInitializer
**File**: `src/main/java/com/lsm/idea_print/config/NewsSourceDataInitializer.java`

**Purpose**: Initializes default news sources with topic assignment

**Execution Order**: @Order(2) - Runs after TopicDataInitializer

**Initialized Sources**:
- **The Neuron Daily**: AI topic, comprehensive selectors
- **AI News**: AI topic, news-focused selectors  
- **VentureBeat AI**: AI topic, business-focused selectors

## Controller Layer

### 1. ContentTopicController
**File**: `src/main/java/com/lsm/idea_print/controller/ContentTopicController.java`

**Purpose**: REST API for content topic management

**Endpoints**:
```
GET    /api/content-topics           - List all topics
GET    /api/content-topics/active   - List active topics
GET    /api/content-topics/{name}   - Get topic by name
POST   /api/content-topics          - Create new topic
PUT    /api/content-topics/{id}     - Update topic
PATCH  /api/content-topics/{id}/toggle - Toggle topic status
DELETE /api/content-topics/{id}     - Delete topic
GET    /api/content-topics/stats    - Get topic account statistics
POST   /api/content-topics/{topicName}/test - Test topic pipeline
POST   /api/content-topics/{topicName}/execute - Execute topic pipeline
```

### 2. Enhanced AccountPromptController
**File**: `src/main/java/com/lsm/idea_print/controller/AccountPromptController.java`

**Purpose**: REST API for account and prompt management

**New Endpoints**:
```
PUT  /api/account-prompts/{userId}/topic - Assign topic to account
GET  /api/account-prompts/by-topic/{topicName} - Get accounts by topic
GET  /api/account-prompts/active-topics - Get accounts with active topics
```

### 3. Enhanced NewsSourceController
**File**: `src/main/java/com/lsm/idea_print/controller/NewsSourceController.java`

**Purpose**: REST API for news source management

**Enhanced Features**:
- Topic assignment in source creation/updates
- Topic-based source filtering
- Source validation with topic context

## Execution Flow Diagrams

### Multi-Topic Pipeline Flow
```
1. ScheduleService.executeMultiTopicPipeline()
   ↓
2. MultiTopicMcpService.executeFullPipelineForAllTopics()
   ↓
3. For each active topic:
   a. TopicBasedCrawlerService.crawlLatestNewsForTopic()
   b. DuplicatePreventionService.isArticleAlreadyPosted()
   c. TopicBasedPostingService.postArticleToTopicAccounts()
      ↓
   d. For each account in topic:
      - TopicBasedContentGenerationService.generatePersonalizedContentForAccount()
      - ThreadsPostService.doPost()
      - MetaToken.incrementPostCount()
   e. DuplicatePreventionService.markArticleAsPosted()
   f. NotificationService.notifySuccess/notifyFailure()
```

### Cross-Topic Pipeline Flow
```
1. ScheduleService.executeCrossTopicPipeline()
   ↓
2. MultiTopicMcpService.executeCrossTopicPipeline()
   ↓
3. TopicBasedCrawlerService.crawlAllTopics()
   ↓
4. For each topic and its articles:
   a. DuplicatePreventionService.isArticleAlreadyPosted()
   b. TopicBasedPostingService.postArticleToTopicAccounts()
   c. Same posting flow as Multi-Topic Pipeline
```

### Universal Pipeline Flow
```
1. ScheduleService.executeUniversalPipeline()
   ↓
2. MultiTopicMcpService.executeUniversalPipeline()
   ↓
3. TopicBasedCrawlerService.crawlLatestNewsForTopic("ai")
   ↓
4. DuplicatePreventionService.isArticleAlreadyPosted()
   ↓
5. TopicBasedPostingService.postArticleToAllTopicAccounts()
   ↓
6. For each account across all topics:
   - TopicBasedContentGenerationService.generatePersonalizedContentForAccount()
   - Account's topic and custom prompt influence generation
   - ThreadsPostService.doPost()
```

## Error Handling Strategy

### Exception Hierarchy
- **McpException**: Base exception for MCP operations
- **CrawlingException**: News crawling failures
- **ContentGenerationException**: Content generation failures  
- **PostingException**: Threads posting failures

### Error Recovery
1. **Graceful Degradation**: Fallback content generation
2. **Retry Logic**: Automatic retries for transient failures
3. **Notification System**: Slack notifications for failures
4. **Logging**: Comprehensive logging with context

### Validation Layer
**File**: `src/main/java/com/lsm/idea_print/validation/ValidationUtils.java`
- Article content validation
- URL format validation
- Content length limits
- Webhook URL validation

## Future Extensibility

### Adding New Topics
1. Create topic via ContentTopicController API
2. Add news sources for the topic
3. Assign accounts to the topic
4. Activate the topic
5. Add scheduled pipeline (optional)

### Adding New Content Types
1. Extend ContentTopic with new prompt templates
2. Update TopicBasedContentGenerationService with new generation logic
3. Add topic-specific validation rules
4. Configure appropriate news sources

### Scaling Considerations
- **Database Indexing**: Topic-based queries are optimized
- **Caching**: Redis for duplicate prevention and potential content caching
- **Rate Limiting**: Built-in request timeouts and retry logic
- **Monitoring**: Comprehensive logging and notification system

This architecture provides a solid foundation for a scalable, multi-topic content management system that can easily accommodate new content types and posting strategies.