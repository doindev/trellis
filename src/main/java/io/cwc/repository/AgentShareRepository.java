package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.AgentShareEntity;

import java.util.List;

@Repository
public interface AgentShareRepository extends JpaRepository<AgentShareEntity, String> {
    List<AgentShareEntity> findByAgentId(String agentId);
    List<AgentShareEntity> findByTargetProjectId(String projectId);
    void deleteByAgentIdAndTargetProjectId(String agentId, String targetProjectId);
    void deleteByAgentId(String agentId);
}
