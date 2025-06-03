package com.lsm.idea_print.dto.request;

import lombok.Data;

@Data
public class saveAccessTokenRequest {
    private String userId;
    private String accessToken;
}
