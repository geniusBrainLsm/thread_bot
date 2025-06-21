package com.lsm.idea_print.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeuhariService {
    private final MetaTokenRepository metaTokenRepository;
    private final Gpt4Service gpt4Service;
    private final WebClient.Builder webClientBuilder;
    private final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";

    public List<Long> performDailyShariForCommenters() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        Flux.fromIterable(accounts)
                .delayElements(Duration.ofSeconds(5)) // 계정 간 5초 딜레이
                .flatMap(account -> performShariForAccount(account.getUserId(), account.getAccessToken()))
                .collectList()
                .doOnNext(results -> {
                    long successCount = results.stream().filter(result -> result.contains("성공")).count();
                    System.out.println("✅ 스하리 스케줄 완료 - 성공: " + successCount + " / 전체: " + results.size());
                })
                .subscribe();
        return accounts.stream().map(MetaToken::getId).toList();
    }

    // 핵심 개선: Rate Limiting과 Retry Logic 적용
    public Mono<String> performShariForAccount(String userId, String accessToken) {
        return getRecentPostsWithComments(userId, accessToken)
                .flatMapMany(posts -> Flux.fromIterable(posts))
                .delayElements(Duration.ofSeconds(2)) // 게시글 간 2초 딜레이
                .flatMap(post -> getPostCommentsWithRetry(post.path("id").asText(), accessToken))
                .flatMap(comments -> Flux.fromIterable(comments))
                .map(comment -> comment.path("from").path("id").asText())
                .distinct()
                .filter(commenterId -> !commenterId.equals(userId))
                .delayElements(Duration.ofSeconds(3)) // 스하리 액션 간 3초 딜레이
                .flatMap(commenterId -> performShariActions(commenterId, accessToken))
                .collectList()
                .map(results -> {
                    long successCount = results.stream().filter(Boolean::booleanValue).count();
                    return userId + " 계정 스하리 완료: " + successCount + "명 성공";
                })
                .onErrorResume(error -> {
                    String message = userId + " 계정 스하리 실패: " + error.getMessage();
                    System.err.println("❌ " + message);
                    return Mono.just(message);
                });
    }

    // 최근 게시글들과 댓글 가져오기
    public Mono<List<JsonNode>> getRecentPostsWithComments(String userId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + userId + "/threads")
                        .queryParam("fields", "id,text,timestamp")
                        .queryParam("limit", "3") // 5개에서 3개로 줄여서 API 부하 감소
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    List<JsonNode> posts = new ArrayList<>();
                    if (response.has("data")) {
                        response.path("data").forEach(posts::add);
                    }
                    return posts;
                })
                .doOnNext(posts -> System.out.println("✅ " + userId + "의 최근 게시글 " + posts.size() + "개 조회"));
    }

    // 핵심 개선: Retry Logic과 더 나은 에러 처리
    public Mono<List<JsonNode>> getPostCommentsWithRetry(String postId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + postId + "/comments")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> {
                            System.err.println("서버 오류 발생 (게시글 ID: " + postId + "), 재시도 예정...");
                            return Mono.error(new RuntimeException("Server error: " + response.statusCode()));
                        }
                )
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(3))) // 3초 간격으로 2번 재시도
                .map(response -> {
                    List<JsonNode> comments = new ArrayList<>();
                    if (response.has("data")) {
                        response.path("data").forEach(comments::add);
                    }
                    return comments;
                })
                .onErrorResume(error -> {
                    System.err.println("댓글 조회 최종 실패 (게시글 ID: " + postId + "): " + error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    // 스하리 수행 (팔로우 + 좋아요 + 리포스트) - 딜레이 추가
    public Mono<Boolean> performShariActions(String targetUserId, String accessToken) {
        return followUser(targetUserId, accessToken)
                .delayElement(Duration.ofSeconds(1)) // 액션 간 1초 딜레이
                .then(likeUserRecentPost(targetUserId, accessToken))
                .delayElement(Duration.ofSeconds(1))
                .then(repostUserRecentPost(targetUserId, accessToken))
                .map(result -> true)
                .doOnNext(success -> System.out.println("✅ " + targetUserId + "에게 스하리 완료"))
                .onErrorResume(error -> {
                    System.err.println("❌ " + targetUserId + " 스하리 실패: " + error.getMessage());
                    return Mono.just(false);
                });
    }

    // 사용자 팔로우
    Mono<Void> followUser(String targetUserId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/following")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("target_user_id", targetUserId))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnNext(result -> System.out.println("✅ " + targetUserId + " 팔로우 완료"));
    }

    // 사용자의 최근 게시글에 좋아요
    Mono<Void> likeUserRecentPost(String targetUserId, String accessToken) {
        return getUserRecentPost(targetUserId, accessToken)
                .flatMap(postId -> {
                    if (postId.isEmpty()) {
                        return Mono.empty();
                    }

                    WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();
                    return client.post()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/" + postId + "/likes")
                                    .queryParam("access_token", accessToken)
                                    .build())
                            .retrieve()
                            .bodyToMono(Void.class)
                            .doOnNext(result -> System.out.println("✅ " + targetUserId + " 게시글 좋아요 완료"));
                });
    }

    // 사용자의 최근 게시글 리포스트
    Mono<Void> repostUserRecentPost(String targetUserId, String accessToken) {
        return getUserRecentPost(targetUserId, accessToken)
                .flatMap(postId -> {
                    if (postId.isEmpty()) {
                        return Mono.empty();
                    }

                    WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();
                    return client.post()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/me/threads")
                                    .queryParam("access_token", accessToken)
                                    .build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of(
                                    "media_type", "REPOST",
                                    "repost_id", postId
                            ))
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .flatMap(container -> {
                                String creationId = container.path("id").asText();
                                return client.post()
                                        .uri(uriBuilder -> uriBuilder
                                                .path("/me/threads_publish")
                                                .queryParam("access_token", accessToken)
                                                .build())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of("creation_id", creationId))
                                        .retrieve()
                                        .bodyToMono(Void.class);
                            })
                            .doOnNext(result -> System.out.println("✅ " + targetUserId + " 게시글 리포스트 완료"));
                });
    }

    // 사용자의 가장 최근 게시글 ID 가져오기
    private Mono<String> getUserRecentPost(String targetUserId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + targetUserId + "/threads")
                        .queryParam("fields", "id")
                        .queryParam("limit", "1")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    if (response.has("data") && response.path("data").size() > 0) {
                        return response.path("data").get(0).path("id").asText();
                    }
                    return "";
                })
                .onErrorResume(error -> {
                    System.err.println("❌ " + targetUserId + " 최근 게시글 조회 실패: " + error.getMessage());
                    return Mono.just("");
                });
    }

    // 맨위게시글 답글 생성 (20개까지만 답글) - 딜레이 추가
    public Mono<String> autoReplyToComments(String userId, String accessToken) {
        return getRecentPostsWithComments(userId, accessToken)
                .flatMapMany(posts -> Flux.fromIterable(posts))
                .delayElements(Duration.ofSeconds(2)) // 게시글 간 딜레이
                .flatMap(post -> {
                    String postId = post.path("id").asText();
                    String postText = post.path("text").asText(); // message -> text로 수정

                    return getPostCommentsWithRetry(postId, accessToken)
                            .flatMapMany(comments -> Flux.fromIterable(comments))
                            .filter(comment -> !comment.path("from").path("id").asText().equals(userId))
                            .take(5) // 댓글 수 제한 (부하 감소)
                            .delayElements(Duration.ofSeconds(3)) // 답글 간 딜레이
                            .flatMap(comment -> {
                                String commentId = comment.path("id").asText();
                                String commentText = comment.path("text").asText(); // message -> text로 수정

                                return generateShortReply(postText, commentText)
                                        .flatMap(replyText -> replyToComment(commentId, replyText, userId, accessToken));
                            });
                })
                .collectList()
                .map(results -> {
                    long successCount = results.stream().filter(Boolean::booleanValue).count();
                    return userId + " 계정 자동 답글 완료: " + successCount + "개 성공";
                })
                .onErrorResume(error -> {
                    String message = userId + " 계정 자동 답글 실패: " + error.getMessage();
                    System.err.println("❌ " + message);
                    return Mono.just(message);
                });
    }

    // 답글생성
    private Mono<String> generateShortReply(String post, String comment) {
        String prompt = post + "<< 글에" + comment + "라고 댓글이 달렸는데 여기에 15자 이내로 답글 달아줘";
        return gpt4Service.generatePost(prompt)
                .map(text -> text.length() > 15 ? text.substring(0, 15) : text);
    }

    // 답글달기
    Mono<Boolean> replyToComment(String commentId, String replyText, String userId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        Map<String, Object> body = new HashMap<>();
        body.put("media_type", "TEXT");
        body.put("text", replyText);
        body.put("reply_to_id", commentId);

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
                                    System.err.println("답글 생성 오류: " + errorBody);
                                    return Mono.error(new RuntimeException("답글 생성 실패: " + response.statusCode()));
                                })
                )
                .bodyToMono(JsonNode.class)
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
                            .bodyToMono(JsonNode.class);
                })
                .map(response -> true)
                .doOnNext(success -> System.out.println("✅ 댓글 답글 완료: " + replyText))
                .onErrorResume(error -> {
                    System.err.println("❌ 댓글 답글 실패: " + error.getMessage());
                    return Mono.just(false);
                });
    }

    @Scheduled(cron = "0 0 16 * * *") // 매일 오후 4시 자동답변
    public void performDailyAutoReply() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        Flux.fromIterable(accounts)
                .delayElements(Duration.ofSeconds(10)) // 계정 간 10초 딜레이
                .flatMap(account -> autoReplyToComments(account.getUserId(), account.getAccessToken()))
                .collectList()
                .doOnNext(results -> {
                    long successCount = results.stream().filter(result -> result.contains("성공")).count();
                    System.out.println("✅ 자동 답글 스케줄 완료 - 성공: " + successCount + " / 전체: " + results.size());
                })
                .subscribe();
    }
}