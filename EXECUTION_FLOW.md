# MCP Bot Execution Flow Documentation

## Overview
This document provides detailed execution flows for all major operations in the Multi-Topic MCP (Meta Content Poster) bot system.

## Table of Contents
1. [Application Startup Flow](#application-startup-flow)
2. [Scheduled Pipeline Flows](#scheduled-pipeline-flows)
3. [Content Generation Flow](#content-generation-flow)
4. [News Crawling Flow](#news-crawling-flow)
5. [Posting Flow](#posting-flow)
6. [API Request Flows](#api-request-flows)
7. [Error Handling Flows](#error-handling-flows)

---

## Application Startup Flow

### 1. Spring Boot Application Initialization
```
IdeaPrintApplication.main()
  ↓
Spring Context Initialization
  ↓
@Order(1) TopicDataInitializer.run()
  ↓ 
@Order(2) NewsSourceDataInitializer.run()
  ↓
WebClient Configuration
  ↓
Scheduling Service Activation
  ↓
API Controllers Ready
```

### 2. TopicDataInitializer Execution Flow
```java
TopicDataInitializer.run()
  ↓
initializeDefaultTopics()
  ├─ Check if "ai" topic exists
  │  └─ Create AI ContentTopic with default prompt
  ├─ Check if "life-hacks" topic exists  
  │  └─ Create Life-Hacks ContentTopic (inactive)
  └─ Check if "general" topic exists
     └─ Create General ContentTopic
  ↓
updateNewsSourcesWithTopics()
  └─ Assign AI topic to existing AI-related sources
```

### 3. NewsSourceDataInitializer Execution Flow
```java
NewsSourceDataInitializer.run()
  ↓
initializeDefaultNewsSources()
  ├─ Get AI topic from database
  ├─ Create "the-neuron-daily" source → Link to AI topic
  ├─ Create "ai-news" source → Link to AI topic  
  └─ Create "venturebeat-ai" source → Link to AI topic
```

---

## Scheduled Pipeline Flows

### 1. Multi-Topic Pipeline (10:30 AM)
```java
@Scheduled(cron = "0 30 10 * * *")
ScheduleService.executeMultiTopicPipeline()
  ↓
MultiTopicMcpService.executeFullPipelineForAllTopics()
  ↓
ContentTopicRepository.findByIsActiveTrue()
  ↓
For each active topic:
  └─ MultiTopicMcpService.executeTopicPipeline(topicName)
     ├─ TopicBasedCrawlerService.crawlLatestNewsForTopic(topicName)
     │  ├─ NewsSourceRepository.findByContentTopicNameAndIsActiveTrue(topicName)
     │  ├─ For each source: crawlFromSource(source)
     │  │  ├─ Jsoup.connect(source.baseUrl)
     │  │  ├─ Extract title, URL, summary using selectors
     │  │  └─ ValidationUtils.isValidNewsArticle(article)
     │  └─ Return first valid article found
     ├─ DuplicatePreventionService.isArticleAlreadyPosted(article)
     │  └─ RedisTemplate.hasKey(article.generateRedisKey())
     ├─ TopicBasedPostingService.postArticleToTopicAccounts(article, topicName)
     │  ├─ MetaTokenRepository.findByContentTopicName(topicName)
     │  └─ For each account in topic:
     │     ├─ TopicBasedContentGenerationService.generatePersonalizedContentForAccount(article, account)
     │     │  ├─ Determine prompt (account → topic → default)
     │     │  ├─ AccountPromptService.formatPromptWithArticle()
     │     │  └─ Gpt4Service.generatePost(prompt)
     │     ├─ ThreadsPostService.doPost(content, userId, accessToken)
     │     │  ├─ Create Threads post container
     │     │  └─ Publish Threads post
     │     └─ MetaToken.incrementPostCount() + save
     ├─ DuplicatePreventionService.markArticleAsPosted(article)
     │  └─ RedisTemplate.opsForValue().set(key, title, 24hours)
     └─ NotificationService.notifySuccess/notifyFailure()
        └─ SlackNotificationService.sendSlackMessage()
```

### 2. Cross-Topic Pipeline (2:00 PM)
```java
@Scheduled(cron = "0 0 14 * * *")
ScheduleService.executeCrossTopicPipeline()
  ↓
MultiTopicMcpService.executeCrossTopicPipeline()
  ↓
TopicBasedCrawlerService.crawlAllTopics()
  ├─ ContentTopicRepository.findByIsActiveTrue()
  └─ For each topic:
     ├─ TopicBasedCrawlerService.crawlAllSourcesForTopic(topicName)
     │  ├─ NewsSourceRepository.findByContentTopicNameAndIsActiveTrue(topicName)
     │  └─ For each source: crawlFromSourceSafe(source)
     └─ Collect all articles by topic
  ↓
For each topic's articles:
  ├─ DuplicatePreventionService.isArticleAlreadyPosted(article)
  ├─ TopicBasedPostingService.postArticleToTopicAccounts(article, topicName)
  │  └─ [Same posting flow as Multi-Topic Pipeline]
  └─ DuplicatePreventionService.markArticleAsPosted(article)
```

### 3. Universal Pipeline (4:00 PM)
```java
@Scheduled(cron = "0 0 16 * * *")
ScheduleService.executeUniversalPipeline()
  ↓
MultiTopicMcpService.executeUniversalPipeline()
  ↓
TopicBasedCrawlerService.crawlLatestNewsForTopic("ai")
  └─ [Same crawling flow as topic-specific]
  ↓
DuplicatePreventionService.isArticleAlreadyPosted(article)
  ↓
TopicBasedPostingService.postArticleToAllTopicAccounts(article)
  ├─ MetaTokenRepository.findByActiveContentTopic()
  └─ For each account across all topics:
     ├─ TopicBasedContentGenerationService.generatePersonalizedContentForAccount(article, account)
     │  ├─ Account prompt takes priority
     │  ├─ Fall back to account's topic default prompt
     │  └─ Generate content with account's topic context
     ├─ ThreadsPostService.doPost(personalizedContent, userId, accessToken)
     └─ MetaToken.incrementPostCount() + save
```

### 4. AI Topic Pipeline (11:00 AM)
```java
@Scheduled(cron = "0 0 11 * * *")
ScheduleService.executeAiTopicPipeline()
  ↓
MultiTopicMcpService.executeTopicPipeline("ai")
  └─ [Same flow as single topic in Multi-Topic Pipeline]
```

### 5. Legacy Pipeline (9:00 AM)
```java
@Scheduled(cron = "0 0 9 * * *")
ScheduleService.executeMcpPipeline()
  ↓
McpService.executeFullPipeline()
  ├─ MultiSiteNewsCrawlerService.crawlLatestAiNews()
  ├─ DuplicatePreventionService.isArticleAlreadyPosted(article)
  ├─ ThreadsPostService.postArticleToAllAccounts(article)
  │  └─ Uses McpContentGenerationService for account-specific content
  └─ DuplicatePreventionService.markArticleAsPosted(article)
```

---

## Content Generation Flow

### 1. Personalized Content Generation
```java
TopicBasedContentGenerationService.generatePersonalizedContentForAccount(article, account)
  ↓
determinePromptForAccount(account, article)
  ├─ Check account.getPrompt() (highest priority)
  ├─ Check account.getContentTopic().getDefaultPrompt()
  ├─ Check getDefaultPromptForSource(article.getSourceName())
  └─ Fall back to getDefaultPromptForTopic("general")
  ↓
AccountPromptService.formatPromptWithArticle(prompt, title, summary, url, sourceName)
  └─ String.format(prompt, title, summary, url, sourceName)
  ↓
Gpt4Service.generatePost(contextualPrompt)
  ├─ WebClient.post() to OpenAI API
  │  ├─ Model: "gpt-4.1"
  │  ├─ Messages: [{"role": "user", "content": prompt}]
  │  └─ Store: true
  ├─ Extract response content
  └─ Return generated text
  ↓
Content validation and fallback handling
  ├─ Check if content is not null/empty
  ├─ Filter and validate content
  └─ Return generateFallbackContent(article) if needed
```

### 2. Topic-Specific Prompt Selection
```java
getDefaultPromptForTopic(topicName)
  ↓
Switch on topicName.toLowerCase():
  ├─ "ai" → AI-focused prompt template
  │  ├─ Tech-savvy tone
  │  ├─ AI emojis (🧠, 🤖, ⚡, 🚀)
  │  └─ Hashtags (#AI #MachineLearning #Tech #Innovation)
  ├─ "life-hacks" → Productivity-focused prompt template
  │  ├─ Friendly, helpful tone
  │  ├─ Productivity emojis (💡, ⚡, 🎯, 🔥)
  │  └─ Hashtags (#LifeHacks #Productivity #Tips #LifeStyle)
  └─ default → General prompt template
     ├─ Casual, engaging tone
     ├─ Generic emojis
     └─ Basic hashtags
```

### 3. Fallback Content Generation
```java
generateFallbackContent(article)
  ↓
Format basic template:
  "🤖 {title}\n\n{summary}\n\n#Content #Update\n\n{url}"
  ↓
Length validation:
  ├─ If title > 100 chars → truncate to 97 + "..."
  ├─ If summary > 120 chars → truncate to 117 + "..."
  └─ If total > 500 chars → use shortened format
```

---

## News Crawling Flow

### 1. Topic-Based Crawling
```java
TopicBasedCrawlerService.crawlLatestNewsForTopic(topicName)
  ↓
NewsSourceRepository.findByContentTopicNameAndIsActiveTrue(topicName)
  ↓
For each source in topic:
  └─ crawlFromSource(source)
     ├─ Jsoup.connect(source.getBaseUrl())
     │  ├─ Timeout: source.getTimeoutMs()
     │  ├─ UserAgent: source.getUserAgent()
     │  └─ Get Document
     ├─ Extract first article using source.getArticleSelector()
     ├─ extractTitle(firstArticle, doc, source)
     │  ├─ Try source.getTitleSelector()
     │  └─ Fall back to "h1, title"
     ├─ extractUrl(firstArticle, source)
     │  ├─ Try source.getUrlSelector()
     │  ├─ Handle relative URLs (prepend baseUrl)
     │  └─ Return absolute URL
     ├─ extractSummary(firstArticle, source)
     │  ├─ Try source.getSummarySelector()
     │  └─ Truncate if > 300 chars
     ├─ Create NewsArticle with source attribution
     ├─ ValidationUtils.isValidNewsArticle(article)
     │  ├─ Check title not empty
     │  ├─ Check URL format
     │  └─ Validate content structure
     └─ Return Optional<NewsArticle>
```

### 2. Multi-Source Crawling
```java
TopicBasedCrawlerService.crawlAllSourcesForTopic(topicName)
  ↓
NewsSourceRepository.findByContentTopicNameAndIsActiveTrue(topicName)
  ↓
sources.stream()
  .map(this::crawlFromSourceSafe)
  .filter(Optional::isPresent)
  .map(Optional::get)
  .peek(article → article.setSourceName(topicName + ":" + article.getSourceName()))
  .toList()
```

### 3. All-Topics Crawling
```java
TopicBasedCrawlerService.crawlAllTopics()
  ↓
ContentTopicRepository.findByIsActiveTrue()
  ↓
For each active topic:
  ├─ crawlAllSourcesForTopic(topic.getName())
  ├─ Collect articles into Map<String, List<NewsArticle>>
  └─ Log article count per topic
```

---

## Posting Flow

### 1. Account-Specific Posting
```java
TopicBasedPostingService.postArticleToTopicAccounts(article, topicName)
  ↓
MetaTokenRepository.findByContentTopicName(topicName)
  ↓
Flux.fromIterable(topicAccounts)
  .flatMap(account → {
    // Generate personalized content
    TopicBasedContentGenerationService.generatePersonalizedContentForAccount(article, account)
    ↓
    ThreadsPostService.doPost(personalizedContent, account.getUserId(), account.getAccessToken())
      ├─ Create Threads post container
      │  ├─ POST /{userId}/threads
      │  ├─ Body: {"media_type": "TEXT", "text": content}
      │  └─ Query: access_token
      ├─ Get creation_id from response
      └─ Publish Threads post
         ├─ POST /{userId}/threads_publish
         ├─ Body: {"creation_id": creationId}
         └─ Return published post response
    ↓
    // Update account statistics
    account.incrementPostCount()
    metaTokenRepository.save(account)
    ↓
    return success/failure status
  })
  .collectList()
  .block()
```

### 2. Cross-Topic Posting
```java
TopicBasedPostingService.postArticleToAllTopicAccounts(article)
  ↓
MetaTokenRepository.findByActiveContentTopic()
  ↓
For each account across all active topics:
  ├─ TopicBasedContentGenerationService.generatePersonalizedContentForAccount(article, account)
  │  └─ Uses account's topic context for personalization
  ├─ ThreadsPostService.doPost(personalizedContent, userId, accessToken)
  └─ account.incrementPostCount() + save
```

### 3. Topic-Separated Posting
```java
TopicBasedPostingService.postArticleToAllTopicsSeparately(article)
  ↓
ContentTopicRepository.findByIsActiveTrue()
  ↓
topics.stream().collect(Collectors.toMap(
  ContentTopic::getName,
  topic → postArticleToTopicAccounts(article, topic.getName())
))
  ↓
Returns Map<String, Boolean> of topic success status
```

---

## API Request Flows

### 1. Topic Management API
```java
POST /api/content-topics
ContentTopicController.createTopic(topic)
  ↓
ContentTopicRepository.save(topic)
  ↓
ApiResponse.success("토픽 생성 성공", savedTopic)
```

```java
GET /api/content-topics/stats
ContentTopicController.getTopicAccountStats()
  ↓
MultiTopicMcpService.getTopicAccountStats()
  ↓
TopicBasedPostingService.getAccountCountByTopic()
  ├─ ContentTopicRepository.findByIsActiveTrue()
  └─ For each topic: MetaTokenRepository.findByContentTopic(topic).size()
```

### 2. Account-Topic Assignment API
```java
PUT /api/account-prompts/{userId}/topic
AccountPromptController.assignTopicToAccount(userId, request)
  ↓
MetaTokenRepository.findByUserId(userId)
  ↓
ContentTopicRepository.findByName(topicName)
  ↓
account.setContentTopic(topic)
  ↓
MetaTokenRepository.save(account)
  ↓
ApiResponse.success("계정 토픽 할당 성공", updatedAccount)
```

### 3. Pipeline Execution API
```java
POST /api/content-topics/{topicName}/execute
ContentTopicController.executeTopicPipeline(topicName)
  ↓
MultiTopicMcpService.executeTopicPipeline(topicName)
  └─ [Same flow as scheduled topic pipeline]
  ↓
Return ApiResponse with success/failure status
```

---

## Error Handling Flows

### 1. Exception Hierarchy Flow
```
Exception occurs in any service
  ↓
Check exception type:
  ├─ CrawlingException
  │  ├─ Log crawling error
  │  ├─ NotificationService.notifyFailure("Crawling Error", message)
  │  └─ Continue with next source/topic
  ├─ ContentGenerationException
  │  ├─ Log generation error  
  │  ├─ NotificationService.notifyFailure("Content Generation Error", message)
  │  └─ Use fallback content generation
  ├─ PostingException
  │  ├─ Log posting error
  │  ├─ NotificationService.notifyFailure("Posting Error", message)
  │  └─ Continue with next account
  └─ General Exception
     ├─ Log general error with context
     ├─ NotificationService.notifyFailure("Pipeline Error", message)
     └─ Stop current operation
```

### 2. Reactive Error Handling
```java
Gpt4Service.generatePost(prompt)
  .filter(content → content != null && !content.trim().isEmpty())
  .doOnNext(content → log.info("Successfully generated content"))
  .doOnError(error → log.error("Failed to generate content", error))
  .onErrorReturn(generateFallbackContent(article))
  .map(String::trim)
```

### 3. Validation Error Flow
```java
ValidationUtils.isValidNewsArticle(article)
  ├─ Check article != null
  ├─ Check title not empty
  ├─ Check URL not empty
  ├─ Check URL format (http/https)
  └─ Return boolean validation result
  ↓
If validation fails:
  ├─ Log validation warning
  ├─ Return Optional.empty()
  └─ Continue with next source
```

### 4. Notification Flow
```java
NotificationService.notifyFailure(title, message)
  ↓
SlackNotificationService.notifyFailure(title, message)
  ↓
Check if slackWebhookUrl is configured
  ├─ If not configured: log warning and return
  └─ If configured: sendSlackMessage(formattedMessage)
     ├─ WebClient.post().uri(slackWebhookUrl)
     ├─ Body: {"text": message}
     └─ Fire-and-forget with error logging
```

---

## Performance Considerations

### 1. Reactive Streams
- Non-blocking I/O for external API calls
- Parallel processing of multiple accounts
- Backpressure handling for large account lists

### 2. Database Optimization
- Indexed queries on topic relationships
- Batch operations for account updates
- Connection pooling for concurrent access

### 3. Caching Strategy
- Redis for duplicate prevention (24-hour TTL)
- Potential content caching for repeated articles
- Topic/account mapping caching

### 4. Rate Limiting
- Configurable timeouts for news source crawling
- Built-in delays between API calls
- Circuit breaker pattern for external services

This execution flow documentation provides a comprehensive view of how the Multi-Topic MCP bot operates, from startup initialization through complex multi-topic content distribution workflows.