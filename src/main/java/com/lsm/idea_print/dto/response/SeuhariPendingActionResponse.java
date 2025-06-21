package com.lsm.idea_print.dto.response;

import com.lsm.idea_print.entity.SeuhariPendingAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeuhariPendingActionResponse {
    private Long id;
    private String userId;
    private String targetUserId;
    private String targetUsername;
    private String actionType; // FOLLOW, LIKE, REPOST, REPLY
    private String postId;
    private String content; // 답글 내용 등
    private SeuhariPendingAction.Status status;

    @Getter
    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public SeuhariPendingActionResponse(SeuhariPendingAction action) {
        this.id = action.getId();
        this.userId = action.getUserId();
        this.targetUserId = action.getTargetUserId();
        this.targetUsername = action.getTargetUsername();
        this.actionType = action.getActionType();
        this.postId = action.getPostId();
        this.content = action.getContent();
        this.status = action.getStatus();
    }
}