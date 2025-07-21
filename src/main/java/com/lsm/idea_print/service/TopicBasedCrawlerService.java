package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.entity.NewsSource;
import com.lsm.idea_print.exception.CrawlingException;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.repository.NewsSourceRepository;
import com.lsm.idea_print.service.interfaces.NewsCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicBasedCrawlerService implements NewsCrawlerService {
    
    private final NewsSourceRepository newsSourceRepository;
    private final ContentTopicRepository contentTopicRepository;
    
    @Override
    public Optional<NewsArticle> crawlLatestAiNews() {
        // Default behavior: crawl from AI topic sources
        return crawlLatestNewsForTopic("ai");
    }
    
    public Optional<NewsArticle> crawlLatestNewsForTopic(String topicName) {
        List<NewsSource> topicSources = newsSourceRepository
                .findByContentTopicNameAndIsActiveTrue(topicName);
        
        if (topicSources.isEmpty()) {
            log.warn("No active news sources configured for topic: {}", topicName);
            return Optional.empty();
        }
        
        // Try each source until we find an article
        for (NewsSource source : topicSources) {
            try {
                Optional<NewsArticle> article = crawlFromSource(source);
                if (article.isPresent()) {
                    log.info("Successfully crawled article from source: {} for topic: {}", 
                        source.getName(), topicName);
                    return article;
                }
            } catch (Exception e) {
                log.warn("Failed to crawl from source: {} for topic: {}, trying next source", 
                    source.getName(), topicName, e);
            }
        }
        
        log.warn("No articles found from any configured source for topic: {}", topicName);
        return Optional.empty();
    }
    
    public Map<String, List<NewsArticle>> crawlAllTopics() {
        Map<String, List<NewsArticle>> topicArticles = new HashMap<>();
        
        List<ContentTopic> activeTopics = contentTopicRepository.findByIsActiveTrue();
        
        for (ContentTopic topic : activeTopics) {
            try {
                List<NewsArticle> articles = crawlAllSourcesForTopic(topic.getName());
                if (!articles.isEmpty()) {
                    topicArticles.put(topic.getName(), articles);
                    log.info("Crawled {} articles for topic: {}", articles.size(), topic.getName());
                }
            } catch (Exception e) {
                log.error("Failed to crawl articles for topic: {}", topic.getName(), e);
            }
        }
        
        return topicArticles;
    }
    
    public List<NewsArticle> crawlAllSourcesForTopic(String topicName) {
        List<NewsSource> topicSources = newsSourceRepository
                .findByContentTopicNameAndIsActiveTrue(topicName);
        
        return topicSources.stream()
                .map(this::crawlFromSourceSafe)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(article -> article.setSourceName(topicName + ":" + article.getSourceName()))
                .toList();
    }
    
    public Optional<NewsArticle> crawlFromTopicSource(ContentTopic topic) {
        List<NewsSource> sources = newsSourceRepository
                .findByContentTopicAndIsActiveTrue(topic);
        
        for (NewsSource source : sources) {
            try {
                Optional<NewsArticle> article = crawlFromSource(source);
                if (article.isPresent()) {
                    article.get().setSourceName(topic.getName() + ":" + source.getName());
                    return article;
                }
            } catch (Exception e) {
                log.warn("Failed to crawl from source: {} for topic: {}", 
                    source.getName(), topic.getName(), e);
            }
        }
        
        return Optional.empty();
    }
    
    private Optional<NewsArticle> crawlFromSourceSafe(NewsSource source) {
        try {
            return crawlFromSource(source);
        } catch (Exception e) {
            log.error("Failed to crawl from source: {}", source.getName(), e);
            return Optional.empty();
        }
    }
    
    private Optional<NewsArticle> crawlFromSource(NewsSource source) {
        try {
            log.info("Crawling from source: {} at {} for topic: {}", 
                source.getName(), source.getBaseUrl(), 
                source.getContentTopic() != null ? source.getContentTopic().getName() : "none");
            
            Document doc = Jsoup.connect(source.getBaseUrl())
                    .timeout(source.getTimeoutMs())
                    .userAgent(source.getUserAgent() != null ? source.getUserAgent() : 
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();
            
            Element firstArticle = doc.selectFirst(source.getArticleSelector() != null ? 
                source.getArticleSelector() : "article, .post, .entry, .article-item");
                
            if (firstArticle == null) {
                log.warn("No article found on {} using selector: {}", 
                    source.getName(), source.getArticleSelector());
                return Optional.empty();
            }
            
            String title = extractTitle(firstArticle, doc, source);
            String url = extractUrl(firstArticle, source);
            String summary = extractSummary(firstArticle, source);
            
            if (title == null || title.trim().isEmpty()) {
                log.warn("Could not extract title from article on {}", source.getName());
                return Optional.empty();
            }
            
            NewsArticle article = new NewsArticle(
                    title.trim(),
                    url != null ? url : source.getBaseUrl(),
                    summary != null ? summary.trim() : "",
                    LocalDateTime.now(),
                    source.getName()
            );
            
            if (!com.lsm.idea_print.validation.ValidationUtils.isValidNewsArticle(article)) {
                log.warn("Crawled article from {} failed validation", source.getName());
                return Optional.empty();
            }
            
            log.info("Successfully crawled article: {} from {} for topic: {}", 
                title, source.getName(), 
                source.getContentTopic() != null ? source.getContentTopic().getName() : "none");
            return Optional.of(article);
            
        } catch (Exception e) {
            log.error("Failed to crawl from source: {} for topic: {}", 
                source.getName(), 
                source.getContentTopic() != null ? source.getContentTopic().getName() : "none", e);
            throw new CrawlingException("Failed to crawl from " + source.getName(), e);
        }
    }
    
    private String extractTitle(Element article, Document doc, NewsSource source) {
        String title = null;
        
        String titleSelector = source.getTitleSelector() != null ? 
            source.getTitleSelector() : "h1, h2, h3, .title, .headline, .post-title";
            
        Element titleElement = article.selectFirst(titleSelector);
        if (titleElement != null) {
            title = titleElement.text();
        }
        
        if (title == null || title.trim().isEmpty()) {
            titleElement = doc.selectFirst("h1, title");
            if (titleElement != null) {
                title = titleElement.text();
            }
        }
        
        return title;
    }
    
    private String extractUrl(Element article, NewsSource source) {
        String urlSelector = source.getUrlSelector() != null ? 
            source.getUrlSelector() : "a[href]";
            
        Element linkElement = article.selectFirst(urlSelector);
        if (linkElement != null) {
            String href = linkElement.attr("href");
            if (href.startsWith("/")) {
                return source.getBaseUrl() + href;
            } else if (href.startsWith("http")) {
                return href;
            }
        }
        return null;
    }
    
    private String extractSummary(Element article, NewsSource source) {
        String summarySelector = source.getSummarySelector() != null ? 
            source.getSummarySelector() : "p, .summary, .excerpt, .description";
            
        Element summaryElement = article.selectFirst(summarySelector);
        if (summaryElement != null) {
            String text = summaryElement.text();
            if (text.length() > 300) {
                return text.substring(0, 297) + "...";
            }
            return text;
        }
        return null;
    }
}