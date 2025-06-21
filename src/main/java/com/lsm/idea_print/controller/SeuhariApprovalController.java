package com.lsm.idea_print.controller;

import com.lsm.idea_print.entity.SeuhariPendingAction;
import com.lsm.idea_print.service.SeuhariApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class SeuhariApprovalController {
    private final SeuhariApprovalService seuhariApprovalService;

    // 승인 대기 목록 조회
    @GetMapping("/pending")
    public ResponseEntity<List<PendingActionDto>> getPendingActions() {
        List<SeuhariPendingAction> actions = seuhariApprovalService.getPendingActions();
        List<SeuhariPendingActionDto> dtos = actions.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // 개별 승인
    @PostMapping("/{actionId}/approve")
    public ResponseEntity<String> approveAction(@PathVariable Long actionId,
                                                @RequestParam String reviewer) {
        boolean success = seuhariApprovalService.approveAction(actionId, reviewer);
        if (success) {
            return ResponseEntity.ok("액션이 승인되어 실행되었습니다.");
        } else {
            return ResponseEntity.badRequest().body("액션 승인에 실패했습니다.");
        }
    }

    // 개별 거부
    @PostMapping("/{actionId}/reject")
    public ResponseEntity<String> rejectAction(@PathVariable Long actionId,
                                               @RequestParam String reviewer) {
        seuhariApprovalService.rejectAction(actionId, reviewer);
        return ResponseEntity.ok("액션이 거부되었습니다.");
    }

    // 배치 승인
    @PostMapping("/batch-approve")
    public ResponseEntity<Map<String, Integer>> batchApprove(
            @RequestBody BatchApprovalRequest request) {
        Map<String, Integer> result = seuhariApprovalService.batchApprove(
                request.getActionIds(), request.getReviewer());
        return ResponseEntity.ok(result);
    }

    private SeuhariPendingActionDto convertToDto(PendingAction action) {
        return PendingActionDto.builder()
                .id(action.getId())
                .userId(action.getUserId())
                .targetUserId(action.getTargetUserId())
                .targetUsername(action.getTargetUsername())
                .actionType(action.getActionType())
                .content(action.getContent())
                .createdAt(action.getCreatedAt())
                .build();
    }
}