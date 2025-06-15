package com.lsm.idea_print.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Table(name = "meta_token")
public class MetaToken {
    @Id
    private Long id;
    private String userId;
    private String accessToken;
    private String prompt;

}
