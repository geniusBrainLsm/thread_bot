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
    //private final String SEUHARIHASHTAG = "#스하리1000명프로젝트";

    public Mono<ApiResponse<List<PostResultResponse>>> postDailyContentForAllAccounts() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        return Flux.fromIterable(accounts)
                .flatMap(account -> {
                    String prompt = Optional.ofNullable(account.getPrompt())
                            .filter(p -> !p.isBlank())
                            .orElse("오늘 하루를 웃음으로 시작하게 할 재미있는 문장을 하나 생성해줘.");

                    return gpt4Service.generatePost(prompt)
                            .map(text -> {  //MONO / FLUX의 map
                                // 처음 10개 게시글에 해시태그 추가 이거 나중에
                                if (account.getPostCount() < 10) {
                                    text += "  그리구..스하리 부탁해 ㅎㅎ  ";
                                    // 카운트 증가 및 DB 저장
                                    account.incrementPostCount();
                                    metaTokenRepository.save(account);
                                }else{
                                    text += "  그리구..스하리 부탁해 ㅎㅎ " ;
                                    account.incrementPostCount();
                                    metaTokenRepository.save(account);
                                }
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

    @Scheduled(cron = "0 0 15 * * *") // 매일 오후 3시
    public void performDailyShariForCommenters() {
        List<MetaToken> accounts = metaTokenRepository.findAll();

        Flux.fromIterable(accounts)
                .flatMap(account -> performShariForAccount(account.getUserId(), account.getAccessToken()))
                .collectList()
                .doOnNext(results -> {
                    long successCount = results.stream().filter(result -> result.contains("성공")).count();
                    System.out.println("\u2705 스하리 스케줄 완료 - 성공: " + successCount + " / 전체: " + results.size());
                })
                .subscribe();
    }

    // 특정 계정의 댓글 작성자들에게 스하리 수행
    private Mono<String> performShariForAccount(String userId, String accessToken) {
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
    private Mono<List<JsonNode>> getRecentPostsWithComments(String userId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(THREADS_API_BASE_URL).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + userId + "/threads")
                        .queryParam("fields", "id,text,timestamp")
                        .queryParam("limit", "10") // 최근 10개 게시글
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
    private Mono<List<JsonNode>> getPostComments(String postId, String accessToken) {
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
    private Mono<Boolean> performShariActions(String targetUserId, String accessToken) {
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

    // 게시글 카운트 관리 유틸리티 메서드들

    /**
     * 특정 계정의 게시글 카운트 초기화
     */
    public Mono<Void> resetPostCount(String userId) {
        return Mono.fromCallable(() -> {
            MetaToken account = metaTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("계정을 찾을 수 없습니다: " + userId));

            account.resetPostCount();
            metaTokenRepository.save(account);
            System.out.println("✅ " + userId + " 계정의 게시글 카운트가 초기화되었습니다.");
            return null;
        });
    }
    @Scheduled(cron = "0 0 16 * * *") // 매일 오후 4시
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

    /**
     * 특정 계정의 최근 게시글 댓글들에 자동 답글
     */
    private Mono<String> autoReplyToComments(String userId, String accessToken) {
        return getRecentPostsWithComments(userId, accessToken)
                .flatMapMany(posts -> Flux.fromIterable(posts))
                .flatMap(post -> {
                    String postId = post.path("id").asText();
                    return getPostComments(postId, accessToken)
                            .flatMapMany(comments -> Flux.fromIterable(comments))
                            .filter(comment -> !comment.path("from").path("id").asText().equals(userId)) // 내 댓글 제외
                            .flatMap(comment -> {
                                String commentId = comment.path("id").asText();
                                String commenterName = comment.path("from").path("username").asText();

                                // 15자 이내 랜덤 답글 생성
                                return generateShortReply(commenterName)
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

    /**
     * 15자 이내 짧은 답글 생성
     */
    private Mono<String> generateShortReply(String commenterName) {
        // 미리 정의된 짧은 답글들
        List<String> shortReplies = List.of(
                "감사해요! 😊",
                "좋은 하루 되세요!",
                "고마워요 ❤️",
                "응원해주셔서 감사!",
                "좋은 의견이에요!",
                "공감해요! 👍",
                "멋져요!",
                "최고예요! 🔥",
                "화이팅! 💪",
                "행복하세요!",
                "좋아요! ✨",
                "멋진 댓글!",
                "따뜻한 말씀 감사!",
                "힘이 돼요!",
                "좋은 생각!"
        );

        // 랜덤 선택 또는 GPT 사용
        String randomReply = shortReplies.get((int) (Math.random() * shortReplies.size()));
        return Mono.just(randomReply);

        // 또는 GPT로 생성하려면:
        // String prompt = commenterName + "님이 댓글을 달아주셨어요. 15자 이내로 감사 인사 답글을 만들어주세요.";
        // return gpt4Service.generatePost(prompt)
        //         .map(text -> text.length() > 15 ? text.substring(0, 15) : text);
    }

    /**
     * 댓글에 답글 달기
     */
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

    /**
     * 수동으로 특정 계정의 댓글에 답글 달기 (API 엔드포인트용)
     */
    public Mono<ApiResponse<String>> replyToRecentComments(String userId) {
        return metaTokenRepository.findByUserId(userId)
                .map(account -> autoReplyToComments(account.getUserId(), account.getAccessToken()))
                .orElse(Mono.error(new RuntimeException("계정을 찾을 수 없습니다: " + userId)))
                .map(result -> ApiResponse.success("답글 작업 완료", result))
                .onErrorResume(error ->
                        Mono.just(ApiResponse.fail("답글 작업 실패: " + error.getMessage()))
                );
    }
    /**
     * 모든 계정의 게시글 카운트 초기화
     */
    public Mono<Void> resetAllPostCounts() {
        return Mono.fromCallable(() -> {
            List<MetaToken> accounts = metaTokenRepository.findAll();
            accounts.forEach(MetaToken::resetPostCount);
            metaTokenRepository.saveAll(accounts);
            System.out.println("✅ 모든 계정의 게시글 카운트가 초기화되었습니다. 대상: " + accounts.size() + "개 계정");
            return null;
        });
    }

    /**
     * 게시글 카운트 조회
     */
    public Mono<Map<String, Integer>> getAllPostCounts() {
        return Mono.fromCallable(() -> {
            List<MetaToken> accounts = metaTokenRepository.findAll();
            Map<String, Integer> countMap = new HashMap<>();
            accounts.forEach(account ->
                    countMap.put(account.getUserId(), account.getPostCount())
            );
            return countMap;
        });
    }
}