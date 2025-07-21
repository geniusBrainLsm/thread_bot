package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.NewsArticle;
import com.lsm.idea_print.service.interfaces.NewsCrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class NewsCrawlerServiceImpl implements NewsCrawlerService {
    
    private static final String NEURON_DAILY_URL = "https://www.theneurondaily.com";
    private static final int TIMEOUT_MS = 10000;
    
    public Optional<NewsArticle> crawlLatestAiNews() {
        try {
            log.info("Crawling latest AI news from The Neuron Daily");
            
            Document doc = Jsoup.connect(NEURON_DAILY_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();
            
            Element firstArticle = doc.selectFirst("article, .post, .entry, .article-item");
            if (firstArticle == null) {
                log.warn("No article found on the page");
                return Optional.empty();
            }
            
            String title = extractTitle(firstArticle, doc);
            String url = extractUrl(firstArticle);
            String summary = extractSummary(firstArticle);
            
            if (title == null || title.trim().isEmpty()) {
                log.warn("Could not extract title from article");
                return Optional.empty();
            }
            
            NewsArticle article = new NewsArticle(
                    title.trim(),
                    url != null ? url : NEURON_DAILY_URL,
                    summary != null ? summary.trim() : "",
                    LocalDateTime.now()
            );
            
            if (!com.lsm.idea_print.validation.ValidationUtils.isValidNewsArticle(article)) {
                log.warn("Crawled article failed validation");
                return Optional.empty();
            }
            
            log.info("Successfully crawled article: {}", title);
            return Optional.of(article);
            
        } catch (Exception e) {
            log.error("Failed to crawl news from The Neuron Daily", e);
            throw new com.lsm.idea_print.exception.CrawlingException("Failed to crawl news", e);
        }
    }
    
    private String extractTitle(Element article, Document doc) {
        String title = null;
        
        Element titleElement = article.selectFirst("h1, h2, h3, .title, .headline, .post-title");
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
    
    private String extractUrl(Element article) {
        Element linkElement = article.selectFirst("a[href]");
        if (linkElement != null) {
            String href = linkElement.attr("href");
            if (href.startsWith("/")) {
                return NEURON_DAILY_URL + href;
            } else if (href.startsWith("http")) {
                return href;
            }
        }
        return null;
    }
    
    private String extractSummary(Element article) {
        Element summaryElement = article.selectFirst("p, .summary, .excerpt, .description");
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