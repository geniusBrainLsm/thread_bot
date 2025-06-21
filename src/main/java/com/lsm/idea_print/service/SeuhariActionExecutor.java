package com.lsm.idea_print.service;

import com.lsm.idea_print.entity.SeuhariPendingAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SeuhariActionExecutor {

    private final SeuhariService seuhariService;

    public Mono<Boolean> execute(SeuhariPendingAction action, String accessToken) {
        switch (action.getActionType()) {
            case "FOLLOW":
                return seuhariService.followUser(action.getTargetUserId(), accessToken)
                        .thenReturn(true).onErrorReturn(false);

            case "LIKE":
                return seuhariService.likeUserRecentPost(action.getTargetUserId(), accessToken)
                        .thenReturn(true).onErrorReturn(false);

            case "REPOST":
                return seuhariService.repostUserRecentPost(action.getTargetUserId(), accessToken)
                        .thenReturn(true).onErrorReturn(false);

            case "REPLY":
                return seuhariService.replyToComment(
                                action.getPostId(), action.getContent(), action.getUserId(), accessToken)
                        .thenReturn(true).onErrorReturn(false);

            default:
                return Mono.just(false);
        }
    }
}