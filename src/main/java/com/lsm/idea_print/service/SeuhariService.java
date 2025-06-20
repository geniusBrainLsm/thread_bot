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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class SeuhariService {
    private MetaTokenRepository metaTokenRepository;
    private final Gpt4Service gpt4Service;
    private final WebClient.Builder webClientBuilder;
    private final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";



    // 특정 계정의 댓글 작성자들에게 스하리 수행
    public Mono<String> performShariForAccount(String userId, String accessToken) {
        return getRecentPostsWithComments(userId, accessToken)
                .flatMapMany(posts -> Flux.fromIterable(posts))
                .flatMap(post -> getPostComments(post.path("id").asText(), accessToken))
                .flatMap(comments -> Flux.fromIterable(comments))
                .map(comment -> comment.path("from").path("id").asText())
                .distinct() // 중복 사용자 제거
                .filter(commenterId -> !commenterId.equals(userId)) // 자기 자신 제외
                .flatMap(commenterId -> performShariActions(commenterId, accessToken))
                .collectList()
                .map(results -> {
                    long successCount = results.stream().filter(Boolean::booleanValue).count();
                    return userId + " 계정 스하리 완료: " + successCount + "명 성공";
                })
                .onErrorResume(error -> {
                    String message = userId + " 계정 스하리 실패: " + error.getMessage();
                    System.err.println("\u274C " + message);
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
                        .queryParam("limit", "5") // 최근 20개 게시글
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

    // 특정 게시글의 댓글 가져오기
    public Mono<List<JsonNode>> getPostComments(String postId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + postId + "/comments")
                        .queryParam("fields", "id,text,from{id,username}")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    List<JsonNode> comments = new ArrayList<>();
                    if (response.has("data")) {
                        response.path("data").forEach(comments::add);
                    }
                    return comments;
                })
                .onErrorResume(error -> {
                    System.err.println("댓글 조회 실패 (게시글 ID: " + postId + "): " + error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    // 스하리 수행 (팔로우 + 좋아요 + 리포스트)
    public Mono<Boolean> performShariActions(String targetUserId, String accessToken) {
        return followUser(targetUserId, accessToken)
                .then(likeUserRecentPost(targetUserId, accessToken))
                .then(repostUserRecentPost(targetUserId, accessToken))
                .map(result -> true)
                .doOnNext(success -> System.out.println("✅ " + targetUserId + "에게 스하리 완료"))
                .onErrorResume(error -> {
                    System.err.println("❌ " + targetUserId + " 스하리 실패: " + error.getMessage());
                    return Mono.just(false);
                });
    }

    // 사용자 팔로우
    private Mono<Void> followUser(String targetUserId, String accessToken) {
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
    private Mono<Void> likeUserRecentPost(String targetUserId, String accessToken) {
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
    private Mono<Void> repostUserRecentPost(String targetUserId, String accessToken) {
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

    //맨위게시글 답글 생성 (20개까지만 답글)
    public Mono<String> autoReplyToComments(String userId, String accessToken) {
        return getRecentPostsWithComments(userId, accessToken)
                .flatMapMany(posts -> Flux.fromIterable(posts))
                .flatMap(post -> {
                    String postId = post.path("id").asText();
                    String postText = post.path("message").asText(); // 수정: path() -> path("message")

                    return getPostComments(postId, accessToken)
                            .flatMapMany(comments -> Flux.fromIterable(comments))
                            .filter(comment -> !comment.path("from").path("id").asText().equals(userId)) // 내 댓글 제외
                            .flatMap(comment -> {
                                String commentId = comment.path("id").asText();
                                String commentText = comment.path("message").asText(); // 댓글 내용 추가

                                // 15자 이내 랜덤 답글 생성 - 수정된 부분
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

    //답글생성
    private Mono<String> generateShortReply(String post, String comment) {
        String prompt = post + "<< 글에" + comment + "라고 댓글이 달렸는데 여기에 15자 이내로 답글 달아줘";
        return gpt4Service.generatePost(prompt)
                .map(text -> text.length() > 15 ? text.substring(0, 15) : text);
    }

    //답글달기
    private Mono<Boolean> replyToComment(String commentId, String replyText, String userId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        Map<String, Object> body = new HashMap<>();
        body.put("media_type", "TEXT");
        body.put("text", replyText);
        body.put("reply_to_id", commentId); // 댓글에 대한 답글

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

                    // 답글 발행
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
                .flatMap(account -> autoReplyToComments(account.getUserId(), account.getAccessToken()))
                .collectList()
                .doOnNext(results -> {
                    long successCount = results.stream().filter(result -> result.contains("성공")).count();
                    System.out.println("✅ 자동 답글 스케줄 완료 - 성공: " + successCount + " / 전체: " + results.size());
                })
                .subscribe();
    }
}
