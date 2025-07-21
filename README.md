# Thread Bot 🤖

An intelligent automated content management system that crawls AI news, generates personalized content using GPT-4, and distributes it across multiple Meta Threads accounts with topic-based organization.

## ✨ Features

### 🔄 Automated Content Pipeline
- **Multi-Topic Content Management**: Supports AI, life-hacks, and general content topics
- **Smart News Crawling**: Crawls multiple news sources using configurable CSS selectors
- **AI-Powered Content Generation**: Uses GPT-4 to create engaging, personalized Threads posts
- **Multi-Account Posting**: Posts to multiple Meta Threads accounts simultaneously
- **Duplicate Prevention**: Redis-based system prevents posting duplicate content within 24 hours

### 🎯 Advanced Personalization
- **Topic-Based Content**: Each topic has unique prompts and writing styles
- **Account-Specific Customization**: Individual accounts can have custom prompts
- **Cross-Topic Distribution**: Same article personalized differently for each topic
- **Fallback Content**: Backup content generation if AI fails

### ⏰ Flexible Scheduling
- **Multiple Daily Pipelines**: 6 different scheduled posting strategies
- **Topic-Specific Timing**: Different topics can post at different times
- **Universal Campaigns**: Share popular content across all accounts

## 🚀 Quick Start

### Prerequisites
- Java 17+
- PostgreSQL
- Redis
- Meta Developer App credentials
- OpenAI API key

### 1. Clone & Setup
```bash
git clone <repository-url>
cd thread_bot
```

### 2. Database Setup
```bash
# Using Docker
docker run -d --name postgres \
  -e POSTGRES_DB=bot \
  -e POSTGRES_USER=bot \
  -e POSTGRES_PASSWORD=1234 \
  -p 5433:5432 postgres:15

docker run -d --name redis -p 6379:6379 redis:alpine
```

### 3. Configuration
Create `application.yml`:
```yaml
meta:
  app-id: your_meta_app_id
  app-secret: your_meta_app_secret

mcp:
  slack:
    webhook-url: ${SLACK_WEBHOOK_URL:}

spring:
  ai:
    openai:
      api-key: your_openai_api_key
      chat:
        options:
          model: gpt-4-turbo-preview
          temperature: 0.7

  datasource:
    url: jdbc:postgresql://localhost:5433/bot
    username: bot
    password: 1234
    
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. Run Application
```bash
./gradlew bootRun
```

## 📚 API Reference

### Account Management
```bash
# Add new account
POST /api/token/save_account
{
  "userId": "user123",
  "accessToken": "token...",
  "accountName": "My Account",
  "prompt": "Custom prompt (optional)"
}

# Update account
PATCH /api/token/save_account/{id}
```

### Content Topics
```bash
# List all topics
GET /api/content-topics

# Create new topic
POST /api/content-topics
{
  "name": "tech",
  "displayName": "Technology",
  "description": "Tech news and updates",
  "defaultPrompt": "You are a tech influencer...",
  "isActive": true
}

# Test topic pipeline
POST /api/content-topics/{topicName}/test
```

### News Sources
```bash
# List sources
GET /api/news-sources

# Add news source
POST /api/news-sources
{
  "name": "tech-news",
  "baseUrl": "https://example.com",
  "articleSelector": "article",
  "titleSelector": "h1, h2",
  "urlSelector": "a[href]",
  "summarySelector": "p, .summary",
  "contentTopicId": 1
}
```

### Manual Posting
```bash
# Run full pipeline
POST /api/mcp/run

# Test without posting
POST /api/mcp/test

# Topic-specific execution
POST /api/content-topics/{topicName}/execute
```

## 🎯 Content Topics

### AI Topic (Default Active)
- **Style**: Tech-savvy, innovative
- **Emojis**: 🧠, 🤖, ⚡, 🚀
- **Hashtags**: #AI #MachineLearning #Tech #Innovation
- **Sources**: The Neuron Daily, AI News, VentureBeat AI

### Life Hacks Topic
- **Style**: Helpful, actionable
- **Emojis**: 💡, ⚡, 🎯, 🔥
- **Hashtags**: #LifeHacks #Productivity #Tips #LifeStyle
- **Status**: Available but inactive by default

### General Topic
- **Style**: Balanced, engaging
- **Use**: Fallback for uncategorized content

## ⏰ Default Schedule

| Time | Pipeline | Description |
|------|----------|-------------|
| 9:00 AM | Legacy MCP | Single source crawling |
| 10:30 AM | Multi-Topic | All topics processed separately |
| 11:00 AM | AI-Specific | AI topic only |
| 12:00 PM | All Sources | All sources for AI topic |
| 2:00 PM | Cross-Topic | Different articles per topic |
| 4:00 PM | Universal | Same AI article to all accounts |

## 🔧 Advanced Configuration

### Custom News Sources
Add new sources by configuring CSS selectors:
```json
{
  "name": "my-news-site",
  "baseUrl": "https://mynews.com",
  "articleSelector": ".news-item",
  "titleSelector": ".news-title",
  "urlSelector": "a",
  "summarySelector": ".news-summary",
  "userAgent": "Mozilla/5.0 ...",
  "timeoutMs": 10000
}
```

### Account Customization
Each account can have:
- **Custom prompts**: Override topic defaults
- **Topic assignment**: Link to specific content topics
- **Post count tracking**: Monitor posting activity

### Duplicate Prevention
Redis-based system with configurable TTL:
- Default: 24-hour duplicate prevention
- Key format: `posted_content:{hash}`
- Automatic cleanup of expired entries

## 🔍 Monitoring & Debugging

### Slack Notifications
Configure webhook for real-time alerts:
- Pipeline execution status
- Posting success/failure
- Error notifications
- Account statistics

### Logs
- Application logs show detailed execution flow
- SQL queries logged for debugging
- Error stack traces for troubleshooting

### Health Checks
- Database connectivity
- Redis connectivity
- Meta API status
- OpenAI API status

## 🛠️ Development

### Adding New Topics
1. Create topic via API or database
2. Configure appropriate news sources
3. Assign accounts to the topic
4. Test with `/test` endpoint

### Adding News Sources
1. Identify target website
2. Inspect HTML structure
3. Configure CSS selectors
4. Test crawling functionality
5. Assign to appropriate topic

### Custom Content Generation
Override prompts at multiple levels:
1. **Account level**: Most specific
2. **Topic level**: Category-specific
3. **Source level**: Source-specific
4. **Global level**: Fallback

## 📝 Troubleshooting

### Common Issues

**Database Connection Failed**
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Verify connection details in application.yml
```

**Redis Connection Failed**
```bash
# Check Redis is running
docker ps | grep redis

# Test Redis connectivity
redis-cli ping
```

**Meta API Errors**
- Verify App ID and App Secret
- Check access token validity
- Ensure Threads API permissions
- Review rate limiting

**Content Generation Issues**
- Verify OpenAI API key
- Check GPT-4 access permissions
- Monitor token usage
- Review prompt formatting

### Support
For issues and feature requests, please check the application logs and verify your configuration against this documentation.

## 🔒 Security Notes

- Store sensitive credentials as environment variables
- Regularly rotate access tokens
- Monitor API usage and rate limits
- Use HTTPS for all external API calls
- Secure database connections

---

**Built with Spring Boot, PostgreSQL, Redis, and powered by OpenAI GPT-4** 🚀