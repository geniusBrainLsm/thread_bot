package com.lsm.idea_print.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BatchRequestIdsRequest {
    private List<Long> ids;
}
