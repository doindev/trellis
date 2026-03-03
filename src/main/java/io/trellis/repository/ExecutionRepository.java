package io.trellis.repository;

import io.trellis.entity.ExecutionEntity;
import io.trellis.entity.ExecutionEntity.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, String> {
    List<ExecutionEntity> findByWorkflowIdOrderByStartedAtDesc(String workflowId);
    Page<ExecutionEntity> findByWorkflowId(String workflowId, Pageable pageable);
    Page<ExecutionEntity> findByStatus(ExecutionStatus status, Pageable pageable);
    Page<ExecutionEntity> findByWorkflowIdAndStatus(String workflowId, ExecutionStatus status, Pageable pageable);
    List<ExecutionEntity> findByStatus(ExecutionStatus status);
    long countByStatus(ExecutionStatus status);

    @Query("SELECT e FROM ExecutionEntity e WHERE e.workflowId IN " +
           "(SELECT w.id FROM WorkflowEntity w WHERE w.projectId = :projectId)")
    Page<ExecutionEntity> findByProjectId(@Param("projectId") String projectId, Pageable pageable);

    @Query("SELECT e FROM ExecutionEntity e WHERE e.status = :status AND e.workflowId IN " +
           "(SELECT w.id FROM WorkflowEntity w WHERE w.projectId = :projectId)")
    Page<ExecutionEntity> findByProjectIdAndStatus(@Param("projectId") String projectId,
                                                    @Param("status") ExecutionStatus status, Pageable pageable);
}
