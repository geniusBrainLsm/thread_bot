package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.ApiResponse;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.entity.ContentTopic;
import com.lsm.idea_print.repository.MetaTokenRepository;
import com.lsm.idea_print.repository.ContentTopicRepository;
import com.lsm.idea_print.service.AccountPromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account-prompts")
@RequiredArgsConstructor
public class AccountPromptController {
    
    private final AccountPromptService accountPromptService;
    private final MetaTokenRepository metaTokenRepository;
    private final ContentTopicRepository contentTopicRepository;
    
    @GetMapping
    public ApiResponse<Map<String, String>> getAllAccountPrompts() {
        Map<String, String> prompts = accountPromptService.getAllAccountPrompts();
        return ApiResponse.success("계정별 프롬프트 조회 성공", prompts);
    }
    
    @GetMapping("/{userId}")
    public ApiResponse<String> getPromptForAccount(@PathVariable String userId) {
        String prompt = accountPromptService.getPromptForAccount(userId);
        return ApiResponse.success("계정 프롬프트 조회 성공", prompt);
    }
    
    @PutMapping("/{userId}")
    public ApiResponse<String> updatePromptForAccount(@PathVariable String userId, @RequestBody Map<String, String> request) {
        String newPrompt = request.get("prompt");
        boolean success = accountPromptService.updatePromptForAccount(userId, newPrompt);
        
        if (success) {
            return ApiResponse.success("계정 프롬프트 업데이트 성공", newPrompt);
        } else {
            return ApiResponse.error("계정을 찾을 수 없습니다");
        }
    }
    
    @GetMapping("/custom")
    public ApiResponse<List<MetaToken>> getAccountsWithCustomPrompts() {
        List<MetaToken> accounts = accountPromptService.getAccountsWithCustomPrompts();
        return ApiResponse.success("커스텀 프롬프트 계정 조회 성공", accounts);
    }
    
    @GetMapping("/default")
    public ApiResponse<List<MetaToken>> getAccountsWithDefaultPrompts() {
        List<MetaToken> accounts = accountPromptService.getAccountsWithDefaultPrompts();
        return ApiResponse.success("기본 프롬프트 계정 조회 성공", accounts);
    }
    
    @PutMapping("/{userId}/topic")
    public ApiResponse<MetaToken> assignTopicToAccount(@PathVariable String userId, @RequestBody Map<String, String> request) {
        String topicName = request.get("topicName");
        
        return metaTokenRepository.findByUserId(userId)
                .map(account -> {
                    ContentTopic topic = contentTopicRepository.findByName(topicName).orElse(null);
                    account.setContentTopic(topic);
                    MetaToken updatedAccount = metaTokenRepository.save(account);
                    return ApiResponse.success("계정 토픽 할당 성공", updatedAccount);
                })
                .orElse(ApiResponse.error("계정을 찾을 수 없습니다"));
    }
    
    @GetMapping("/by-topic/{topicName}")
    public ApiResponse<List<MetaToken>> getAccountsByTopic(@PathVariable String topicName) {
        List<MetaToken> accounts = metaTokenRepository.findByContentTopicName(topicName);
        return ApiResponse.success("토픽별 계정 조회 성공", accounts);
    }
    
    @GetMapping("/active-topics")
    public ApiResponse<List<MetaToken>> getAccountsWithActiveTopics() {
        List<MetaToken> accounts = metaTokenRepository.findByActiveContentTopic();
        return ApiResponse.success("활성 토픽 계정 조회 성공", accounts);
    }
}