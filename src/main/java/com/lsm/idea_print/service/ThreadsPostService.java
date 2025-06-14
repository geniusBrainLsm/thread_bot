package com.lsm.idea_print.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lsm.idea_print.config.ThreadsAccountProperties;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThreadsPostService {

    private final Gpt4Service gpt4Service;
    private final WebClient.Builder webClientBuilder;
    private final MetaTokenRepository metaTokenRepository;

    private final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void postDailyGptContent() {
        metaTokenRepository.findAll().forEach(account -> {
            String prompt = account.getPrompt();
            if (prompt == null || prompt.isBlank()) {
                prompt = "오늘 하루를 웃음으로 시작하게 할 재미있는 문장을 하나 생성해줘.";
            }

        // 예시: 최신 계정 기준으로 게시
            gpt4Service.generatePost(prompt)
                    .flatMap(text -> postToThreads(text, account))
                    .doOnSuccess(resp -> System.out.println("✅ Threads에 글 게시 완료: " + resp))
                    .doOnError(error -> System.err.println("❌ 게시 실패: " + error.getMessage()))
                    .block();
        });
    }

    public Mono<JsonNode> postToThreads(String text, String threadsUserId, String accessToken) {
        WebClient webClient = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return webClient.post()
                .uri("/" + threadsUserId + "/threads")
                .bodyValue(Map.of(
                        "media_type", "TEXT",
                        "text", text,
                        "access_token", accessToken
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(container -> {
                    String creationId = container.path("id").asText();

                    return webClient.post()
                            .uri("/" + threadsUserId + "/threads_publish")
                            .bodyValue(Map.of(
                                    "creation_id", creationId,
                                    "access_token", accessToken
                            ))
                            .retrieve()
                            .bodyToMono(JsonNode.class);
                });
    }
}
