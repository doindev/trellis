package io.trellis.repository;

import io.trellis.entity.WorkflowVersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, String> {
    List<WorkflowVersionEntity> findByWorkflowIdOrderByVersionNumberDesc(String workflowId);
    Page<WorkflowVersionEntity> findByWorkflowIdOrderByVersionNumberDesc(String workflowId, Pageable pageable);
    Page<WorkflowVersionEntity> findByWorkflowIdOrderByPublishedAtDesc(String workflowId, Pageable pageable);
    Page<WorkflowVersionEntity> findByWorkflowIdAndPublishedOrderByPublishedAtDesc(String workflowId, boolean published, Pageable pageable);
    Optional<WorkflowVersionEntity> findFirstByWorkflowIdOrderByVersionNumberDesc(String workflowId);
    Optional<WorkflowVersionEntity> findFirstByWorkflowIdAndPublishedFalseOrderByPublishedAtDesc(String workflowId);
    void deleteByWorkflowId(String workflowId);
}
