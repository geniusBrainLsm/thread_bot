package com.lsm.idea_print.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostResultResponse {
    private String userId;
    private boolean success;
    private String message;
}