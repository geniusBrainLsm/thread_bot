package com.lsm.idea_print.repository;

import com.lsm.idea_print.entity.ContentTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentTopicRepository extends JpaRepository<ContentTopic, Long> {
    Optional<ContentTopic> findByNameAndIsActiveTrue(String name);
    List<ContentTopic> findByIsActiveTrue();
    Optional<ContentTopic> findByName(String name);
}