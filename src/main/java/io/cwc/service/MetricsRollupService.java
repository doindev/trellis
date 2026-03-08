package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.entity.ExecutionEntity;
import io.cwc.entity.ExecutionMetrics5minEntity;
import io.cwc.entity.ExecutionMetricsHourlyEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.entity.ExecutionEntity.ExecutionMode;
import io.cwc.entity.ExecutionEntity.ExecutionStatus;
import io.cwc.repository.ExecutionMetrics5minRepository;
import io.cwc.repository.ExecutionMetricsHourlyRepository;
import io.cwc.repository.ExecutionRepository;
import io.cwc.repository.WorkflowRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Periodically aggregates execution data into hourly and 5-minute rollup tables.
 * Uses instance synchronization via TriggerLockService so only one instance
 * in the cluster runs the rollup at a time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsRollupService {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration RETENTION_5MIN = Duration.ofHours(48);

    private final ExecutionRepository executionRepository;
    private final ExecutionMetricsHourlyRepository hourlyRepository;
    private final ExecutionMetrics5minRepository fiveMinRepository;
    private final WorkflowRepository workflowRepository;
    private final TriggerLockService triggerLockService;

    /** Self-reference through proxy so @Transactional works on internal calls. */
    @Setter(onMethod_ = {@Autowired, @Lazy})
    private MetricsRollupService self;

    private volatile boolean backfillDone = false;

    /**
     * On startup, backfill rollup tables from existing execution data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!triggerLockService.tryAcquire("_system", "metricsBackfill")) {
            log.info("Metrics backfill: another instance holds the lock, skipping");
            backfillDone = true;
            return;
        }
        try {
            backfill();
        } finally {
            triggerLockService.release("_system", "metricsBackfill");
            backfillDone = true;
        }
    }

    /**
     * Incremental rollup: runs every 60 seconds, processes recent executions.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 70_000)
    public void incrementalRollup() {
        if (!backfillDone) return;
        if (!triggerLockService.tryAcquire("_system", "metricsRollup")) {
            return;
        }
        try {
            Instant now = Instant.now();
            self.rollup5min(now.minus(Duration.ofMinutes(15)), now);
            self.rollupHourly(now.minus(Duration.ofHours(3)), now);
            self.cleanup5min(now);
        } catch (Exception e) {
            log.error("Incremental metrics rollup failed", e);
        } finally {
            triggerLockService.release("_system", "metricsRollup");
        }
    }

    /**
     * Backfill both rollup tables from existing execution data.
     */
    private void backfill() {
        Instant now = Instant.now();
        log.info("Starting metrics backfill...");

        // 5min: backfill last 48 hours
        Instant fiveMinStart = now.minus(RETENTION_5MIN);
        self.rollup5min(fiveMinStart, now);

        // Hourly: backfill last 90 days
        Instant hourlyStart = now.minus(Duration.ofDays(90));
        self.rollupHourly(hourlyStart, now);

        log.info("Metrics backfill complete");
    }

    @Transactional
    public void rollup5min(Instant windowStart, Instant windowEnd) {
        Instant bucketStart = truncateTo5min(windowStart);
        Instant bucketEnd = truncateTo5min(windowEnd).plus(FIVE_MINUTES);

        // Query using bucket-aligned boundaries so we recapture ALL executions
        // belonging to any bucket we're about to delete and recreate
        List<ExecutionEntity> executions = executionRepository.findForRollup(
                bucketStart, bucketEnd, ExecutionMode.MANUAL);
        Map<String, String> workflowProjectMap = loadWorkflowProjectMap();

        Map<String, Map<Instant, int[]>> grouped = groupIntoBuckets(
                executions, workflowProjectMap, FIVE_MINUTES);

        fiveMinRepository.deleteByBucketTimeRange(bucketStart, bucketEnd);

        List<ExecutionMetrics5minEntity> entities = new ArrayList<>();
        for (var projectEntry : grouped.entrySet()) {
            String projectId = projectEntry.getKey();
            for (var bucketEntry : projectEntry.getValue().entrySet()) {
                int[] vals = bucketEntry.getValue();
                entities.add(ExecutionMetrics5minEntity.builder()
                        .projectId(projectId)
                        .bucketTime(bucketEntry.getKey())
                        .totalCount(vals[0])
                        .successCount(vals[1])
                        .errorCount(vals[2])
                        .canceledCount(vals[3])
                        .totalDurationMs(toLong(vals[4], vals[5]))
                        .finishedCount(vals[6])
                        .build());
            }
        }
        if (!entities.isEmpty()) {
            fiveMinRepository.saveAll(entities);
        }
        log.debug("5min rollup: processed {} executions into {} buckets ({} to {})",
                executions.size(), entities.size(), windowStart, windowEnd);
    }

    @Transactional
    public void rollupHourly(Instant windowStart, Instant windowEnd) {
        Instant bucketStart = windowStart.truncatedTo(ChronoUnit.HOURS);
        Instant bucketEnd = windowEnd.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));

        // Query using bucket-aligned boundaries so we recapture ALL executions
        // belonging to any bucket we're about to delete and recreate
        List<ExecutionEntity> executions = executionRepository.findForRollup(
                bucketStart, bucketEnd, ExecutionMode.MANUAL);
        Map<String, String> workflowProjectMap = loadWorkflowProjectMap();

        Map<String, Map<Instant, int[]>> grouped = groupIntoBuckets(
                executions, workflowProjectMap, Duration.ofHours(1));

        hourlyRepository.deleteByBucketTimeRange(bucketStart, bucketEnd);

        List<ExecutionMetricsHourlyEntity> entities = new ArrayList<>();
        for (var projectEntry : grouped.entrySet()) {
            String projectId = projectEntry.getKey();
            for (var bucketEntry : projectEntry.getValue().entrySet()) {
                int[] vals = bucketEntry.getValue();
                entities.add(ExecutionMetricsHourlyEntity.builder()
                        .projectId(projectId)
                        .bucketTime(bucketEntry.getKey())
                        .totalCount(vals[0])
                        .successCount(vals[1])
                        .errorCount(vals[2])
                        .canceledCount(vals[3])
                        .totalDurationMs(toLong(vals[4], vals[5]))
                        .finishedCount(vals[6])
                        .build());
            }
        }
        if (!entities.isEmpty()) {
            hourlyRepository.saveAll(entities);
        }
        log.debug("Hourly rollup: processed {} executions into {} buckets ({} to {})",
                executions.size(), entities.size(), windowStart, windowEnd);
    }

    @Transactional
    public void cleanup5min(Instant now) {
        Instant cutoff = now.minus(RETENTION_5MIN);
        int deleted = fiveMinRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.debug("Cleaned up {} expired 5-min metrics rows", deleted);
        }
    }

    /**
     * Groups executions into time buckets per project.
     * Returns Map&lt;projectId, Map&lt;bucketTime, int[7]&gt;&gt;
     * where int[7] = {total, success, error, canceled, durationHigh, durationLow, finishedCount}
     * (duration split into two ints to avoid using long[] — reassembled via toLong)
     */
    private Map<String, Map<Instant, int[]>> groupIntoBuckets(
            List<ExecutionEntity> executions, Map<String, String> workflowProjectMap, Duration bucketSize) {
        Map<String, Map<Instant, int[]>> result = new LinkedHashMap<>();
        long bucketMs = bucketSize.toMillis();

        for (ExecutionEntity exec : executions) {
            String projectId = workflowProjectMap.get(exec.getWorkflowId());
            if (projectId == null) continue;
            if (exec.getStartedAt() == null) continue;

            long epochMs = exec.getStartedAt().toEpochMilli();
            long bucketEpoch = (epochMs / bucketMs) * bucketMs;
            Instant bucket = Instant.ofEpochMilli(bucketEpoch);

            int[] vals = result
                    .computeIfAbsent(projectId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(bucket, k -> new int[7]);

            vals[0]++; // total
            if (exec.getStatus() == ExecutionStatus.SUCCESS) {
                vals[1]++;
            } else if (exec.getStatus() == ExecutionStatus.ERROR) {
                vals[2]++;
            } else if (exec.getStatus() == ExecutionStatus.CANCELED) {
                vals[3]++;
            }

            if (exec.getStartedAt() != null && exec.getFinishedAt() != null) {
                long durationMs = exec.getFinishedAt().toEpochMilli() - exec.getStartedAt().toEpochMilli();
                // Accumulate duration across two int slots to handle overflow
                long current = toLong(vals[4], vals[5]) + durationMs;
                vals[4] = (int) (current >>> 32);
                vals[5] = (int) current;
                vals[6]++;
            }
        }
        return result;
    }

    private Map<String, String> loadWorkflowProjectMap() {
        Map<String, String> map = new HashMap<>();
        for (WorkflowEntity wf : workflowRepository.findAll()) {
            if (wf.getProjectId() != null) {
                map.put(wf.getId(), wf.getProjectId());
            }
        }
        return map;
    }

    private Instant truncateTo5min(Instant instant) {
        long epochMs = instant.toEpochMilli();
        long fiveMinMs = FIVE_MINUTES.toMillis();
        return Instant.ofEpochMilli((epochMs / fiveMinMs) * fiveMinMs);
    }

    private static long toLong(int high, int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }
}
