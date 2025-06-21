package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.ApiResponse;
import com.lsm.idea_print.dto.request.BatchRequestIdsRequest;
import com.lsm.idea_print.dto.response.SeuhariPendingActionResponse;
import com.lsm.idea_print.service.SeuhariApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class SeuhariApprovalController {
    private final SeuhariApprovalService seuhariApprovalService;

    // 승인 대기 목록 조회
    @GetMapping("/pending")
    public ApiResponse<List<SeuhariPendingActionResponse>> getPendingActions() {
        return ApiResponse.success("조회 성공",seuhariApprovalService.getSeuhariPendingActions());
    }

    // 개별 승인
    @PostMapping("/{id}/approve")
    public ApiResponse<Mono<Boolean>> approveAction(@PathVariable Long id) {
        return ApiResponse.success("개별 승인 성공", seuhariApprovalService.approveAction(id));
    }

    // 개별 거부
//    @PostMapping("/{id}/reject")
//    public ResponseEntity<String> rejectAction(@PathVariable Long id) {
//        seuhariApprovalService.rejectAction(id);
//        return ResponseEntity.ok("액션이 거부되었습니다.");
//    }

    // 배치 승인
    @PostMapping("/batch-approve")
    public ApiResponse<List<Long>> batchApprove(
            @RequestBody BatchRequestIdsRequest request) {
        return ApiResponse.success("배치 처리 성공", seuhariApprovalService.batchApprove(request));
    }

}