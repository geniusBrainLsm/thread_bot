package com.lsm.idea_print.repository;

import com.lsm.idea_print.entity.MetaToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetaTokenRepository extends JpaRepository<MetaToken, Long> {

    /**
     * userId로 계정 조회
     */
    Optional<MetaToken> findByUserId(String userId);

    /**
     * 게시글 카운트가 특정 값보다 작은 계정들 조회
     */
    List<MetaToken> findByPostCountLessThan(Integer postCount);

    /**
     * 게시글 카운트가 특정 값 이상인 계정들 조회
     */
    List<MetaToken> findByPostCountGreaterThanEqual(Integer postCount);

    /**
     * 모든 계정의 게시글 카운트 합계 조회
     */
    @Query("SELECT SUM(m.postCount) FROM MetaToken m")
    Long getTotalPostCount();

    /**
     * 게시글 카운트 기준으로 정렬하여 조회
     */
    List<MetaToken> findAllByOrderByPostCountDesc();

    /**
     * userId 존재 여부 확인
     */
    boolean existsByUserId(String userId);
}