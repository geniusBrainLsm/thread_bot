package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.ApiResponse;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.service.MultiTopicMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content-topics")
@RequiredArgsConstructor
public class ContentTopicController {
    
    private final ContentTopicRepository contentTopicRepository;
    private final MultiTopicMcpService multiTopicMcpService;
    
    @GetMapping
    public ApiResponse<List<ContentTopic>> getAllTopics() {
        List<ContentTopic> topics = contentTopicRepository.findAll();
        return ApiResponse.success("컨텐츠 토픽 목록 조회 성공", topics);
    }
    
    @GetMapping("/active")
    public ApiResponse<List<ContentTopic>> getActiveTopics() {
        List<ContentTopic> activeTopics = contentTopicRepository.findByIsActiveTrue();
        return ApiResponse.success("활성 토픽 목록 조회 성공", activeTopics);
    }
    
    @GetMapping("/{name}")
    public ApiResponse<ContentTopic> getTopicByName(@PathVariable String name) {
        return contentTopicRepository.findByName(name)
                .map(topic -> ApiResponse.success("토픽 조회 성공", topic))
                .orElse(ApiResponse.error("토픽을 찾을 수 없습니다"));
    }
    
    @PostMapping
    public ApiResponse<ContentTopic> createTopic(@RequestBody ContentTopic topic) {
        ContentTopic savedTopic = contentTopicRepository.save(topic);
        return ApiResponse.success("토픽 생성 성공", savedTopic);
    }
    
    @PutMapping("/{id}")
    public ApiResponse<ContentTopic> updateTopic(@PathVariable Long id, @RequestBody ContentTopic topic) {
        return contentTopicRepository.findById(id)
                .map(existingTopic -> {
                    existingTopic.setName(topic.getName());
                    existingTopic.setDisplayName(topic.getDisplayName());
                    existingTopic.setDescription(topic.getDescription());
                    existingTopic.setDefaultPrompt(topic.getDefaultPrompt());
                    existingTopic.setIsActive(topic.getIsActive());
                    
                    ContentTopic updatedTopic = contentTopicRepository.save(existingTopic);
                    return ApiResponse.success("토픽 수정 성공", updatedTopic);
                })
                .orElse(ApiResponse.error("토픽을 찾을 수 없습니다"));
    }
    
    @PatchMapping("/{id}/toggle")
    public ApiResponse<ContentTopic> toggleTopicStatus(@PathVariable Long id) {
        return contentTopicRepository.findById(id)
                .map(topic -> {
                    topic.setIsActive(!topic.getIsActive());
                    ContentTopic updatedTopic = contentTopicRepository.save(topic);
                    return ApiResponse.success("토픽 상태 변경 성공", updatedTopic);
                })
                .orElse(ApiResponse.error("토픽을 찾을 수 없습니다"));
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTopic(@PathVariable Long id) {
        if (contentTopicRepository.existsById(id)) {
            contentTopicRepository.deleteById(id);
            return ApiResponse.success("토픽 삭제 성공", null);
        } else {
            return ApiResponse.error("토픽을 찾을 수 없습니다");
        }
    }
    
    @GetMapping("/stats")
    public ApiResponse<Map<String, Integer>> getTopicAccountStats() {
        Map<String, Integer> stats = multiTopicMcpService.getTopicAccountStats();
        return ApiResponse.success("토픽별 계정 통계 조회 성공", stats);
    }
    
    @PostMapping("/{topicName}/test")
    public ApiResponse<String> testTopicPipeline(@PathVariable String topicName) {
        try {
            multiTopicMcpService.executeTestingPipeline(topicName);
            return ApiResponse.success("토픽 테스트 파이프라인 실행 완료", "테스트가 성공적으로 실행되었습니다");
        } catch (Exception e) {
            return ApiResponse.success("토픽 테스트 실행 실패", e.getMessage());
        }
    }
    
    @PostMapping("/{topicName}/execute")
    public ApiResponse<Boolean> executeTopicPipeline(@PathVariable String topicName) {
        try {
            boolean success = multiTopicMcpService.executeTopicPipeline(topicName);
            if (success) {
                return ApiResponse.success("토픽 파이프라인 실행 성공", true);
            } else {
                return ApiResponse.error("토픽 파이프라인 실행 실패");
            }
        } catch (Exception e) {
            return ApiResponse.error("토픽 파이프라인 실행 중 오류");
        }
    }
}