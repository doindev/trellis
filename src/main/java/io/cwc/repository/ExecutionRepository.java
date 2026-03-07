package io.cwc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ExecutionEntity;
import io.cwc.entity.ExecutionEntity.ExecutionMode;
import io.cwc.entity.ExecutionEntity.ExecutionStatus;

import java.time.Instant;
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

    // Production metrics: exclude manual executions, published workflows only
    @Query("SELECT e FROM ExecutionEntity e WHERE e.mode <> :excludeMode " +
           "AND e.workflowId IN (SELECT w.id FROM WorkflowEntity w WHERE w.published = true AND w.projectId = :projectId)")
    Page<ExecutionEntity> findProductionByProjectId(@Param("projectId") String projectId,
                                                    @Param("excludeMode") ExecutionEntity.ExecutionMode excludeMode,
                                                    Pageable pageable);

    @Query("SELECT e FROM ExecutionEntity e WHERE e.mode <> :excludeMode " +
           "AND e.workflowId IN (SELECT w.id FROM WorkflowEntity w WHERE w.published = true AND w.projectId IN :projectIds)")
    Page<ExecutionEntity> findProductionByProjectIds(@Param("projectIds") java.util.List<String> projectIds,
                                                     @Param("excludeMode") ExecutionEntity.ExecutionMode excludeMode,
                                                     Pageable pageable);

    @Query("SELECT e FROM ExecutionEntity e WHERE e.startedAt >= :start AND e.startedAt < :end " +
           "AND e.mode <> :excludeMode")
    List<ExecutionEntity> findForRollup(@Param("start") Instant start,
                                        @Param("end") Instant end,
                                        @Param("excludeMode") ExecutionEntity.ExecutionMode excludeMode);
}
