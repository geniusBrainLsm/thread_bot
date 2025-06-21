package com.lsm.idea_print.repository;

import com.lsm.idea_print.entity.SeuhariPendingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SeuhariPendingActionRepository extends JpaRepository<SeuhariPendingAction, Long> {
    List<SeuhariPendingAction> findByStatusOrderByCreatedAtDesc(String status);
    List<SeuhariPendingAction> findByUserIdAndStatus(String userId, String status);
    long countByUserIdAndStatusAndCreatedAtAfter(String userId, SeuhariPendingAction.Status status, LocalDateTime after);
}