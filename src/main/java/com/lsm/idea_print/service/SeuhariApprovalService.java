package com.lsm.idea_print.service;

import com.lsm.idea_print.entity.SeuhariPendingAction;
import com.lsm.idea_print.repository.SeuhariPendingActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SeuhariApprovalService {
    private final SeuhariPendingActionRepository seuhariPendingActionRepository;
    private final SeuhariService seuhariService;

    // 승인 대기 액션 생성 (기존 즉시 실행 대신)
    public void createPendingAction(String userId, String targetUserId, String actionType,
                                    String postId, String content) {
        // 일일 제출 한도 체크 (예: 하루 20개)
        LocalDateTime today = LocalDateTime.now().with(LocalTime.MIN);
        long todayCount = seuhariPendingActionRepository
                .countByUserIdAndStatusAndCreatedAtAfter(userId, SeuhariPendingAction.Status.PENDING, today);

        if (todayCount >= 100) {
            throw new RuntimeException("일일 액션 제출 한도를 초과했습니다. (20개/일)");
        }

        SeuhariPendingAction action = new SeuhariPendingAction();
        action.setUserId(userId);
        action.setTargetUserId(targetUserId);
        action.setActionType(actionType);
        action.setPostId(postId);
        action.setContent(content);
        action.setStatus(SeuhariPendingAction.Status.PENDING);
        //action.setCreatedAt(LocalDateTime.now());

        // 타겟 사용자명 조회해서 저장 (관리자가 보기 편하게)
        action.setTargetUsername(getUsernameById(targetUserId));

        seuhariPendingActionRepository.save(action);
        System.out.println("✅ 승인 대기 액션 생성: " + actionType + " -> " + targetUserId);
    }

    // 승인 대기 목록 조회
    public List<SeuhariPendingAction> getPendingActions() {
        return seuhariPendingActionRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    // 개별 액션 승인
    public boolean approveAction(Long actionId, String reviewerName) {
        Optional<SeuhariPendingAction> actionOpt = seuhariPendingActionRepository.findById(actionId);
        if (actionOpt.isEmpty()) return false;

        SeuhariPendingAction action = actionOpt.get();
        if (!SeuhariPendingAction.Status.PENDING.equals(action.getStatus())) return false;

        try {
            // 실제 액션 실행
            boolean success = executeAction(action);

            if (success) {
                action.setStatus(SeuhariPendingAction.Status.APPROVED);
                //action.setReviewedAt(LocalDateTime.now());
                //action.setReviewedBy(reviewerName);
                seuhariPendingActionRepository.save(action);
                return true;
            }
        } catch (Exception e) {
            System.err.println("액션 실행 실패: " + e.getMessage());
        }
        return false;
    }


    // 배치 승인 (선택된 액션들을 한번에 승인)
    public Map<String, Integer> batchApprove(List<Long> actionIds, String reviewerName) {
        int successCount = 0;
        int failCount = 0;

        for (Long actionId : actionIds) {
            // 액션 간 안전한 딜레이 (30초)
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            boolean success = approveAction(actionId, reviewerName);
            if (success) successCount++;
            else failCount++;
        }

        return Map.of("success", successCount, "fail", failCount);
    }

    // 실제 액션 실행
    private boolean executeAction(SeuhariPendingAction action) {
        String accessToken = getAccessTokenByUserId(action.getUserId());

        switch (action.getActionType()) {
            case "FOLLOW":
                return Boolean.TRUE.equals(seuhariService.followUser(action.getTargetUserId(), accessToken)
                        .then(Mono.fromCallable(() -> true))
                        .onErrorReturn(false)
                        .block());

            case "LIKE":
                return Boolean.TRUE.equals(seuhariService.likeUserRecentPost(action.getTargetUserId(), accessToken)
                        .then(Mono.fromCallable(() -> true))
                        .onErrorReturn(false)
                        .block());

            case "REPOST":
                return Boolean.TRUE.equals(seuhariService.repostUserRecentPost(action.getTargetUserId(), accessToken)
                        .then(Mono.fromCallable(() -> true))
                        .onErrorReturn(false)
                        .block());

            case "REPLY":
                return Boolean.TRUE.equals(seuhariService.replyToComment(action.getPostId(), action.getContent(),
                                action.getUserId(), accessToken)
                        .onErrorReturn(false)
                        .block());

            default:
                return false;
        }
    }

    private String getUsernameById(String userId) {
        // Threads API로 사용자명 조회
        // 구현 필요
        return "user_" + userId.substring(0, 8);
    }

    private String getAccessTokenByUserId(String userId) {
        // MetaTokenRepository에서 액세스 토큰 조회
        // 구현 필요
        return "";
    }
}
