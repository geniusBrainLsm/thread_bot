package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.request.BatchRequestIdsRequest;
import com.lsm.idea_print.dto.response.SeuhariPendingActionResponse;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.entity.SeuhariPendingAction;
import com.lsm.idea_print.repository.MetaTokenRepository;
import com.lsm.idea_print.repository.SeuhariPendingActionRepository;
import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class SeuhariApprovalService {
    private final SeuhariPendingActionRepository seuhariPendingActionRepository;
    private final SeuhariActionExecutor actionExecutor;
    private final MetaTokenRepository metaTokenRepository;
    private final ThreadsPostService threadsPostService;
    // 승인 대기 액션 생성 (기존 즉시 실행 대신)
    public Mono<Void> createPendingAction(String userId, String targetUserId, String actionType,
                                          String postId, String content) {
        // 1. 오늘 날짜 기준으로 제한 체크
        LocalDateTime today = LocalDateTime.now().with(LocalTime.MIN);

        return Mono.fromCallable(() ->
                        seuhariPendingActionRepository
                                .countByUserIdAndStatusAndCreatedAtAfter(userId, SeuhariPendingAction.Status.PENDING, today)
                )
                .flatMap(count -> {
                    if (count >= 100) {
                        return Mono.error(new RuntimeException("일일 액션 제출 한도를 초과했습니다. (100개/일)"));
                    }

                    SeuhariPendingAction action = new SeuhariPendingAction();
                    action.setUserId(userId);
                    action.setTargetUserId(targetUserId);
                    action.setActionType(actionType);
                    action.setPostId(postId);
                    action.setContent(content);
                    action.setStatus(SeuhariPendingAction.Status.PENDING);

                    // 2. 비동기로 username 조회 후 저장
                    return getUsernameById(targetUserId)
                            .doOnNext(username -> {
                                action.setTargetUsername(username);
                                seuhariPendingActionRepository.save(action);
                                System.out.println("✅ 승인 대기 액션 생성: " + actionType + " -> " + targetUserId);
                            })
                            .then(); // Mono<Void> 리턴
                });
    }

    // 승인 대기 목록 조회
    public List<SeuhariPendingActionResponse> getSeuhariPendingActions() {
        List<SeuhariPendingAction> actions =
                seuhariPendingActionRepository.findByStatusOrderByCreatedAtDesc(SeuhariPendingAction.Status.PENDING);
        return actions.stream()
                .map(SeuhariPendingActionResponse::new) // id만 추출
                .toList();
    }



    // 개별 액션 승인
    public Mono<Boolean> approveAction(Long actionId) {
        return Mono.justOrEmpty(seuhariPendingActionRepository.findById(actionId))
                .filter(action -> action.getStatus() == SeuhariPendingAction.Status.PENDING)
                .flatMap(action -> {
                    String accessToken = getAccessTokenByUserId(action.getUserId());
                    return actionExecutor.execute(action, accessToken)
                            .doOnNext(success -> {
                                if (success) {
                                    action.setStatus(SeuhariPendingAction.Status.APPROVED);
                                    seuhariPendingActionRepository.save(action);
                                }
                            });
                })
                .defaultIfEmpty(false);
    }


    // 배치 승인 (선택된 액션들을 한번에 승인)
    public List<Long> batchApprove(BatchRequestIdsRequest request) {
        for (Long actionId : request.getIds()) {
            // 액션 간 안전한 딜레이 (30초)
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            approveAction(actionId);
        }

        return request.getIds();
    }

    // 실제 액션 실행


    private Mono<String> getUsernameById(String userId) {
        return Mono.fromSupplier(() -> getAccessTokenByUserId(userId))
                .flatMap(accessToken ->
                        threadsPostService.getUserInfo(accessToken)
                                .map(json -> json.path("username").asText(null))
                                .onErrorResume(error -> {
                                    System.err.println("사용자명 조회 실패: " + error.getMessage());
                                    return Mono.just("unknown_" + userId.substring(0, 6));
                                })
                );
    }

    private String getAccessTokenByUserId(String userId) {
        return metaTokenRepository.findByUserId(userId)
                .map(MetaToken::getAccessToken)
                .orElseThrow(() ->
                        new IllegalArgumentException("해당 userId에 대한 액세스 토큰이 존재하지 않습니다: " + userId));
    }
}
