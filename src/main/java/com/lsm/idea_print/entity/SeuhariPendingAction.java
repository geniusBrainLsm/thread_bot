package com.lsm.idea_print.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "pending_actions")
public class SeuhariPendingAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String targetUserId;
    private String targetUsername;
    private String actionType; // FOLLOW, LIKE, REPOST, REPLY
    private String postId;
    private String content; // 답글 내용 등
    private Status status;

    @Getter
    public enum Status{
        PENDING, APPROVED, REJECTED
    }

}