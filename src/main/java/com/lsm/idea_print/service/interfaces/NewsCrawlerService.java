package com.lsm.idea_print.service.interfaces;

import com.lsm.idea_print.dto.NewsArticle;
import java.util.Optional;

public interface NewsCrawlerService {
    Optional<NewsArticle> crawlLatestAiNews();
}