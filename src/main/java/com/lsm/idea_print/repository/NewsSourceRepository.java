package com.lsm.idea_print.repository;

import com.lsm.idea_print.entity.NewsSource;
import com.lsm.idea_print.entity.ContentTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByIsActiveTrue();
    Optional<NewsSource> findByNameAndIsActiveTrue(String name);
    List<NewsSource> findByContentTopicAndIsActiveTrue(ContentTopic contentTopic);
    List<NewsSource> findByContentTopicNameAndIsActiveTrue(String topicName);
}