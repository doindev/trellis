package io.trellis.repository;

import io.trellis.entity.ExecutionMetricsHourlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionMetricsHourlyRepository extends JpaRepository<ExecutionMetricsHourlyEntity, String> {

    List<ExecutionMetricsHourlyEntity> findByProjectIdAndBucketTimeBetween(
            String projectId, Instant start, Instant end);

    List<ExecutionMetricsHourlyEntity> findByProjectIdInAndBucketTimeBetween(
            List<String> projectIds, Instant start, Instant end);

    Optional<ExecutionMetricsHourlyEntity> findByProjectIdAndBucketTime(
            String projectId, Instant bucketTime);

    @Modifying
    @Query("DELETE FROM ExecutionMetricsHourlyEntity m WHERE m.bucketTime >= :start AND m.bucketTime < :end")
    int deleteByBucketTimeRange(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT MAX(m.bucketTime) FROM ExecutionMetricsHourlyEntity m")
    Optional<Instant> findMaxBucketTime();
}
