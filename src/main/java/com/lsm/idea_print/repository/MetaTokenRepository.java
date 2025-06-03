package com.lsm.idea_print.repository;

import com.lsm.idea_print.entity.MetaToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface MetaTokenRepository extends JpaRepository<MetaToken, Long> {


    Optional<MetaToken> findTopByOrderByIdDesc();

}