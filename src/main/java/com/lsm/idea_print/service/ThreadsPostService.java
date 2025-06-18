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

    private final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";

    public Mono<ApiResponse<List<PostResultResponse>>> postDailyContentForAllAccounts() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        return Flux.fromIterable(accounts)
                .flatMap(account -> {
                    String prompt = Optional.ofNullable(account.getPrompt())
                            .filter(p -> !p.isBlank())
                            .orElse("오늘 하루를 웃음으로 시작하게 할 재미있는 문장을 하나 생성해줘.");

                    return gpt4Service.generatePost(prompt)
                            .map(text -> {  //MONO / FLUX의 map
                                //TODO : 게시글 주제 붙이기. 어캐하는지 아직모름
                                text += "  그리구..스하리 부탁해 ㅎㅎ  ";
                                // 카운트 증가 및 DB 저장
                                account.incrementPostCount();
                                metaTokenRepository.save(account);

                                return text;
                            })
                            .flatMapMany(text -> splitTextByLimit(text, 500))
                            .index()
                            .concatMap(tuple -> {
                                long index = tuple.getT1();
                                String partText = tuple.getT2();
                                return postSingleTextWithReplyTracking(partText, account.getUserId(), account.getAccessToken(), index);
                            })
                            .collectList()
                            .map(list -> new PostResultResponse(account.getUserId(), true, "게시 성공"))
                            .onErrorResume(error -> {
                                String message = "게시 실패: " + error.getMessage();
                                System.err.println("\u274C " + account.getUserId() + ": " + message);
                                return Mono.just(new PostResultResponse(account.getUserId(), false, message));
                            });
                })
                .collectList()
                .map(results -> ApiResponse.success("모든 계정에 게시 완료", results));
    }

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void postDailyGptContent() {
        postDailyContentForAllAccounts()
                .doOnNext(response -> {
                    long successCount = response.getData().stream().filter(PostResultResponse::isSuccess).count();
                    System.out.println("\u2705 스케줄 완료 - 성공: " + successCount + " / 전체: " + response.getData().size());
                })
                .subscribe();
    }

    private Flux<String> splitTextByLimit(String text, int limit) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            result.add(text.substring(start, end));
            start = end;
        }
        return Flux.fromIterable(result);
    }

    private Mono<JsonNode> postSingleTextWithReplyTracking(String text, String userId, String accessToken, long index) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return Mono.deferContextual(contextView -> {
            String replyToId = contextView.hasKey("lastPostId") && index > 0
                    ? contextView.get("lastPostId")
                    : null;

            Map<String, Object> body = new HashMap<>();
            body.put("media_type", "TEXT");
            body.put("text", text);

            if (replyToId != null) {
                body.put("reply_to_id", replyToId);
            }

            return client.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + userId + "/threads")
                            .queryParam("access_token", accessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.err.println("API 오류 응답: " + errorBody);
                                        return Mono.error(new RuntimeException("API 오류: " + response.statusCode() + " - " + errorBody));
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .doOnNext(response -> {
                        System.out.println("✅ 스레드 생성 성공: " + response.path("id").asText());
                    })
                    .flatMap(container -> {
                        String creationId = container.path("id").asText();

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
                                })
                                .contextWrite(ctx -> ctx.put("lastPostId", container.path("id").asText()));
                    })
                    .doOnError(error -> {
                        System.err.println("❌ 사용자 " + userId + "의 postSingleTextWithReplyTracking에서 오류 발생: " + error.getMessage());
                    });
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








}