package com.lsm.idea_print.service.interfaces;

import com.lsm.idea_print.dto.NewsArticle;
import reactor.core.publisher.Mono;

public interface ContentGenerationService {
    String generateThreadsPost(NewsArticle article);
    Mono<String> generateThreadsPostAsync(NewsArticle article);
}