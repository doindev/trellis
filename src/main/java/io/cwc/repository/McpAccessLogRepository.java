package io.cwc.repository;

import io.cwc.entity.McpAccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface McpAccessLogRepository extends JpaRepository<McpAccessLogEntity, String> {

    List<McpAccessLogEntity> findByEndpointPathOrderByTimestampDesc(String endpointPath);

    List<McpAccessLogEntity> findByUsernameOrderByTimestampDesc(String username);

    /** Delete all log entries older than the given date (retention cleanup) */
    @Modifying
    @Query("DELETE FROM McpAccessLogEntity e WHERE e.logDate < :cutoff")
    int deleteByLogDateBefore(LocalDate cutoff);
}
