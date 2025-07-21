package com.lsm.idea_print.service;

import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPromptService {
    
    private final MetaTokenRepository metaTokenRepository;
    
    private static final String DEFAULT_PROMPT = """
            You are a tech influencer creating engaging Threads posts about AI news.
            
            Transform this AI news article into a casual, engaging Threads post:
            
            Title: %s
            Summary: %s
            URL: %s
            Source: %s
            
            Guidelines:
            - Keep it under 280 characters
            - Use a casual, fun tone like a tech influencer
            - Include relevant emojis (🧠, 🤖, ⚡, 🚀, etc.)
            - Add relevant hashtags (#AI #Tech #Innovation)
            - Make it shareable and engaging
            - Don't be overly promotional
            - Include the URL at the end
            
            Create the Threads post:
            """;
    
    public String getPromptForAccount(String userId) {
        return metaTokenRepository.findByUserId(userId)
                .map(MetaToken::getPrompt)
                .filter(prompt -> prompt != null && !prompt.trim().isEmpty())
                .orElse(DEFAULT_PROMPT);
    }
    
    public Map<String, String> getAllAccountPrompts() {
        List<MetaToken> accounts = metaTokenRepository.findAll();
        
        return accounts.stream()
                .collect(Collectors.toMap(
                    MetaToken::getUserId,
                    account -> account.getPrompt() != null && !account.getPrompt().trim().isEmpty() 
                        ? account.getPrompt() 
                        : DEFAULT_PROMPT
                ));
    }
    
    public boolean updatePromptForAccount(String userId, String newPrompt) {
        return metaTokenRepository.findByUserId(userId)
                .map(account -> {
                    account.setPrompt(newPrompt);
                    metaTokenRepository.save(account);
                    log.info("Updated prompt for account: {}", userId);
                    return true;
                })
                .orElse(false);
    }
    
    public List<MetaToken> getAccountsWithCustomPrompts() {
        return metaTokenRepository.findAll().stream()
                .filter(account -> account.getPrompt() != null && !account.getPrompt().trim().isEmpty())
                .toList();
    }
    
    public List<MetaToken> getAccountsWithDefaultPrompts() {
        return metaTokenRepository.findAll().stream()
                .filter(account -> account.getPrompt() == null || account.getPrompt().trim().isEmpty())
                .toList();
    }
    
    public String formatPromptWithArticle(String prompt, String title, String summary, String url, String sourceName) {
        try {
            return String.format(prompt, title, summary, url, sourceName != null ? sourceName : "Unknown");
        } catch (Exception e) {
            log.warn("Failed to format prompt with article data, using default format", e);
            return String.format(DEFAULT_PROMPT, title, summary, url, sourceName != null ? sourceName : "Unknown");
        }
    }
}