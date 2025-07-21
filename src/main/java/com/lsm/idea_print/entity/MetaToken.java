package com.lsm.idea_print.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "meta_token")
public class MetaToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String accessToken;
//
//    @Column(columnDefinition = "TEXT")
//    private String prompt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_topic_id")
    private ContentTopic contentTopic;
    
    @Column(name = "account_name")
    private String accountName;
    
    @Column(name = "account_description")
    private String accountDescription;

    // 게시글 카운트 필드 추가 (기본값 0)
    @Builder.Default
    @Column(name = "post_count", nullable = false)
    private Integer postCount = 0;

    // 생성일시
    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 수정일시
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 업데이트 시 자동으로 수정일시 갱신
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 게시글 카운트 증가 메서드
    public void incrementPostCount() {
        this.postCount++;
    }

    // 게시글 카운트 초기화 메서드 (필요시 사용)
    public void resetPostCount() {
        this.postCount = 0;
    }
}