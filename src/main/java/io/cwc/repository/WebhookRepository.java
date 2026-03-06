package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.WebhookEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookRepository extends JpaRepository<WebhookEntity, String> {
    Optional<WebhookEntity> findByMethodAndPath(String method, String path);
    Optional<WebhookEntity> findByMethodAndPathAndIsTest(String method, String path, boolean isTest);
    List<WebhookEntity> findByWorkflowId(String workflowId);
    List<WebhookEntity> findByWorkflowIdAndIsTest(String workflowId, boolean isTest);
    List<WebhookEntity> findByMethodAndIsTestAndPathContaining(String method, boolean isTest, String substring);
    List<WebhookEntity> findByIsTest(boolean isTest);
    void deleteByWorkflowId(String workflowId);
    void deleteByWorkflowIdAndIsTest(String workflowId, boolean isTest);
}
