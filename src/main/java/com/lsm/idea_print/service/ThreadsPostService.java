package com.lsm.idea_print.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lsm.idea_print.dto.response.PostResultResponse;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import com.lsm.idea_print.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class ThreadsPostService {

    private final Gpt4Service gpt4Service;
    private final WebClient.Builder webClientBuilder;
    private final MetaTokenRepository metaTokenRepository;
    private final TopicBasedContentGenerationService contentGenerationService;

    private final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";

    public Mono<ApiResponse<List<PostResultResponse>>> postDailyContentForAllAccounts() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        return Flux.fromIterable(accounts)
                .flatMap(account -> {
                    String prompt = Optional.ofNullable(account.getPrompt())
                            .filter(p -> !p.isBlank())
                            .orElse("오늘 하루를 웃음으로 시작하게 할 재미있는 문장을 하나 생성해줘.");

                    return gpt4Service.generatePost(prompt)
                            .flatMap(text -> {
                                text += " 팔로우하고 글을 매일 받아봐! 반하리는 무조건!! ";
                                // 카운트 증가 및 DB 저장
                                account.incrementPostCount();
                                metaTokenRepository.save(account);
                                return doPost(text, account.getUserId(), account.getAccessToken())
                                        .then(Mono.just(new PostResultResponse(account.getUserId(), true, "게시 성공!!")));
                            })
                            .onErrorResume(error -> {
                                String errorMessage = "게시 실패 ㅠㅠ" + error.getMessage();
                                System.out.println("❌ " + account.getUserId() + ": " + errorMessage);
                                return Mono.just(new PostResultResponse(account.getUserId(), false, errorMessage));
                            });

                })
                .collectList()
                .map(results -> ApiResponse.success("모든 계정에 게시 완료", results));
    }



    public  Mono<JsonNode> doPost(String text, String userId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        Map<String, Object> body = new HashMap<>();
        body.put("media_type", "TEXT");
        body.put("text", text);

        return client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + userId + "/threads")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> {
                    System.out.println("✅ 스레드 생성 성공: " + response.path("id").asText());
                })
                .flatMap(container -> {
                    String creationId = container.path("id").asText(); //id는 25454234... 이거

                    return client.post()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/" + userId + "/threads_publish")
                                    .queryParam("access_token", accessToken)
                                    .build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("creation_id", creationId))
                            .retrieve()
                            .onStatus(
                                    status -> status.is4xxClientError() || status.is5xxServerError(),
                                    response -> response.bodyToMono(String.class)
                                            .flatMap(errorBody -> {
                                                System.err.println("게시 오류 응답: " + errorBody);
                                                return Mono.error(new RuntimeException("게시 오류: " + response.statusCode() + " - " + errorBody));
                                            })
                            )
                            .bodyToMono(JsonNode.class)
                            .doOnNext(publishResponse -> {
                                System.out.println("✅ 스레드 게시 성공: " + publishResponse.path("id").asText());
                            });
                })
                .doOnError(error -> {
                    System.err.println("❌ 사용자 " + userId + "의 doPost에서 오류 발생: " + error.getMessage());
                });
    }

    public Mono<JsonNode> getUserInfo(String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me")
                        .queryParam("fields", "id,username,name,threads_profile_picture_url")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    System.err.println("사용자 정보 가져오기 오류: " + errorBody);
                                    return Mono.error(new RuntimeException("사용자 정보 오류: " + response.statusCode() + " - " + errorBody));
                                })
                )
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> {
                    System.out.println("✅ 사용자 정보: " + response.toPrettyString());
                });
    }




    public boolean postToAllAccounts(String content) {
        try {
            List<MetaToken> accounts = metaTokenRepository.findAll();
            if (accounts.isEmpty()) {
                System.out.println("❌ 게시할 계정이 없습니다.");
                return false;
            }

            List<Boolean> results = Flux.fromIterable(accounts)
                    .flatMap(account -> 
                        doPost(content, account.getUserId(), account.getAccessToken())
                                .map(response -> true)
                                .onErrorReturn(false)
                    )
                    .collectList()
                    .block();

            boolean allSuccess = results != null && results.stream().allMatch(success -> success);
            System.out.println(allSuccess ? 
                "✅ 모든 계정에 MCP 콘텐츠 게시 성공" : 
                "❌ 일부 계정에서 MCP 콘텐츠 게시 실패");

            return allSuccess;
        } catch (Exception e) {
            System.err.println("❌ MCP 콘텐츠 게시 중 오류: " + e.getMessage());
            return false;
        }
    }
    
    public boolean postArticleToAllAccounts(com.lsm.idea_print.dto.NewsArticle article) {
        try {
            List<MetaToken> accounts = metaTokenRepository.findAll();
            if (accounts.isEmpty()) {
                System.out.println("❌ 게시할 계정이 없습니다.");
                return false;
            }

            List<Boolean> results = Flux.fromIterable(accounts)
                    .flatMap(account -> {
                        // Generate account-specific content
                        String accountSpecificContent = contentGenerationService
                                .generateThreadsPostForAccount(article, account.getUserId());
                        
                        return doPost(accountSpecificContent, account.getUserId(), account.getAccessToken())
                                .map(response -> {
                                    // Increment post count for successful posts
                                    account.incrementPostCount();
                                    metaTokenRepository.save(account);
                                    return true;
                                })
                                .onErrorReturn(false);
                    })
                    .collectList()
                    .block();

            boolean allSuccess = results != null && results.stream().allMatch(success -> success);
            System.out.println(allSuccess ? 
                "✅ 모든 계정에 개인화된 MCP 콘텐츠 게시 성공" : 
                "❌ 일부 계정에서 개인화된 MCP 콘텐츠 게시 실패");

            return allSuccess;
        } catch (Exception e) {
            System.err.println("❌ 개인화된 MCP 콘텐츠 게시 중 오류: " + e.getMessage());
            return false;
        }
    }
}