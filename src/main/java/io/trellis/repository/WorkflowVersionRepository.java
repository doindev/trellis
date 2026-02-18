package io.trellis.repository;

import io.trellis.entity.WorkflowVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, String> {
    List<WorkflowVersionEntity> findByWorkflowIdOrderByVersionNumberDesc(String workflowId);
    void deleteByWorkflowId(String workflowId);
}
