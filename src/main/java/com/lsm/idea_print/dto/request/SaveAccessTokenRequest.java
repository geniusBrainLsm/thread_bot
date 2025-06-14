package com.lsm.idea_print.dto.request;

import lombok.Data;

@Data
public class SaveAccessTokenRequest {
    private String userId;
    private String accessToken;
    private String prompt;
}