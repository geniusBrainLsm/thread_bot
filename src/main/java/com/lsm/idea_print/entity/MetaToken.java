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
public class MetaToken extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String accessToken;

    private String prompt;

    // 게시글 카운트 필드 추가 (기본값 0)
    @Builder.Default
    @Column(name = "post_count", nullable = false)
    private Integer postCount = 0;

    // 게시글 카운트 증가 메서드
    public void incrementPostCount() {
        this.postCount++;
    }

    // 게시글 카운트 초기화 메서드 (필요시 사용)
    public void resetPostCount() {
        this.postCount = 0;
    }
}