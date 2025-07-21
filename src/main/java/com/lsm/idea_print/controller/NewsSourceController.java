package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.ApiResponse;
import com.lsm.idea_print.entity.NewsSource;
import com.lsm.idea_print.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news-sources")
@RequiredArgsConstructor
public class NewsSourceController {
    
    private final NewsSourceRepository newsSourceRepository;
    
    @GetMapping
    public ApiResponse<List<NewsSource>> getAllNewsSources() {
        List<NewsSource> sources = newsSourceRepository.findAll();
        return ApiResponse.success("뉴스 소스 목록 조회 성공", sources);
    }
    
    @GetMapping("/active")
    public ApiResponse<List<NewsSource>> getActiveNewsSources() {
        List<NewsSource> activeSources = newsSourceRepository.findByIsActiveTrue();
        return ApiResponse.success("활성 뉴스 소스 목록 조회 성공", activeSources);
    }
    
    @PostMapping
    public ApiResponse<NewsSource> createNewsSource(@RequestBody NewsSource newsSource) {
        NewsSource savedSource = newsSourceRepository.save(newsSource);
        return ApiResponse.success("뉴스 소스 생성 성공", savedSource);
    }
    
    @PutMapping("/{id}")
    public ApiResponse<NewsSource> updateNewsSource(@PathVariable Long id, @RequestBody NewsSource newsSource) {
        return newsSourceRepository.findById(id)
                .map(existingSource -> {
                    existingSource.setName(newsSource.getName());
                    existingSource.setBaseUrl(newsSource.getBaseUrl());
                    existingSource.setArticleSelector(newsSource.getArticleSelector());
                    existingSource.setTitleSelector(newsSource.getTitleSelector());
                    existingSource.setUrlSelector(newsSource.getUrlSelector());
                    existingSource.setSummarySelector(newsSource.getSummarySelector());
                    existingSource.setUserAgent(newsSource.getUserAgent());
                    existingSource.setTimeoutMs(newsSource.getTimeoutMs());
                    existingSource.setIsActive(newsSource.getIsActive());
                    
                    NewsSource updatedSource = newsSourceRepository.save(existingSource);
                    return ApiResponse.success("뉴스 소스 수정 성공", updatedSource);
                })
                .orElse(ApiResponse.error("뉴스 소스를 찾을 수 없습니다"));
    }
    
    @PatchMapping("/{id}/toggle")
    public ApiResponse<NewsSource> toggleNewsSourceStatus(@PathVariable Long id) {
        return newsSourceRepository.findById(id)
                .map(source -> {
                    source.setIsActive(!source.getIsActive());
                    NewsSource updatedSource = newsSourceRepository.save(source);
                    return ApiResponse.success("뉴스 소스 상태 변경 성공", updatedSource);
                })
                .orElse(ApiResponse.error("뉴스 소스를 찾을 수 없습니다"));
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNewsSource(@PathVariable Long id) {
        if (newsSourceRepository.existsById(id)) {
            newsSourceRepository.deleteById(id);
            return ApiResponse.success("뉴스 소스 삭제 성공", null);
        } else {
            return ApiResponse.error("뉴스 소스를 찾을 수 없습니다");
        }
    }
}