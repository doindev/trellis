package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ExecutionMetrics5minEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionMetrics5minRepository extends JpaRepository<ExecutionMetrics5minEntity, String> {

    List<ExecutionMetrics5minEntity> findByProjectIdAndBucketTimeBetween(
            String projectId, Instant start, Instant end);

    List<ExecutionMetrics5minEntity> findByProjectIdInAndBucketTimeBetween(
            List<String> projectIds, Instant start, Instant end);

    Optional<ExecutionMetrics5minEntity> findByProjectIdAndBucketTime(
            String projectId, Instant bucketTime);

    @Modifying
    @Query("DELETE FROM ExecutionMetrics5minEntity m WHERE m.bucketTime >= :start AND m.bucketTime < :end")
    int deleteByBucketTimeRange(@Param("start") Instant start, @Param("end") Instant end);

    @Modifying
    @Query("DELETE FROM ExecutionMetrics5minEntity m WHERE m.bucketTime < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
