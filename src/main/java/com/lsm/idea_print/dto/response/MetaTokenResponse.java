package com.lsm.idea_print.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
public class MetaTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("user_id")
    private String userId;


}
