package com.lsm.idea_print.service;

import com.lsm.idea_print.service.interfaces.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
public class SlackNotificationService implements NotificationService {
    
    private final WebClient webClient;

    @Value("${mcp.slack.webhook-url:}")
    private String slackWebhookUrl;

    public SlackNotificationService(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder.build();
    }
    
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (!com.lsm.idea_print.validation.ValidationUtils.isValidWebhookUrl(slackWebhookUrl)) {
            log.warn("Slack webhook URL is not configured or invalid. Notifications will be disabled.");
        }
    }

    
    public void notifySuccess(String articleTitle) {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            log.warn("Slack webhook URL not configured, skipping notification");
            return;
        }
        
        try {
            String message = String.format("✅ Posted to Threads: %s", articleTitle);
            sendSlackMessage(message);
            log.info("Sent success notification to Slack for: {}", articleTitle);
        } catch (Exception e) {
            log.error("Failed to send success notification to Slack", e);
        }
    }
    
    public void notifyFailure(String articleTitle, String errorMessage) {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            log.warn("Slack webhook URL not configured, skipping notification");
            return;
        }
        
        try {
            String message = String.format("❌ Failed to post %s: %s", articleTitle, errorMessage);
            sendSlackMessage(message);
            log.info("Sent failure notification to Slack for: {}", articleTitle);
        } catch (Exception e) {
            log.error("Failed to send failure notification to Slack", e);
        }
    }
    
    public void notifyNoCrawledContent() {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            log.warn("Slack webhook URL not configured, skipping notification");
            return;
        }
        
        try {
            String message = "⚠️ MCP Pipeline: No new AI articles found to post";
            sendSlackMessage(message);
            log.info("Sent no content notification to Slack");
        } catch (Exception e) {
            log.error("Failed to send no content notification to Slack", e);
        }
    }
    
    public void notifyDuplicateSkipped(String articleTitle) {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            log.warn("Slack webhook URL not configured, skipping notification");
            return;
        }
        
        try {
            String message = String.format("ℹ️ Skipped duplicate article: %s", articleTitle);
            sendSlackMessage(message);
            log.info("Sent duplicate notification to Slack for: {}", articleTitle);
        } catch (Exception e) {
            log.error("Failed to send duplicate notification to Slack", e);
        }
    }
    
    private void sendSlackMessage(String message) {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            log.warn("Slack webhook URL not configured, cannot send message");
            return;
        }
        
        Map<String, String> payload = Map.of("text", message);
        
        webClient.post()
                .uri(slackWebhookUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.debug("Slack message sent successfully"),
                    error -> log.error("Failed to send Slack message", error)
                );
    }
}