package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.MetricsResponse;
import io.cwc.dto.MetricsResponse.MetricsBucket;
import io.cwc.dto.MetricsResponse.MetricsSummary;
import io.cwc.entity.ExecutionMetrics5minEntity;
import io.cwc.entity.ExecutionMetricsHourlyEntity;
import io.cwc.repository.ExecutionMetrics5minRepository;
import io.cwc.repository.ExecutionMetricsHourlyRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MetricsController {

    private static final Duration FIVE_MIN_THRESHOLD = Duration.ofHours(48);

    private final ExecutionMetrics5minRepository fiveMinRepository;
    private final ExecutionMetricsHourlyRepository hourlyRepository;

    /**
     * Returns pre-aggregated execution metrics for a time range.
     * Automatically selects the appropriate granularity table:
     * - Ranges <= 48h: 5-minute granularity
     * - Ranges > 48h: hourly granularity
     */
    @GetMapping("/api/metrics")
    public MetricsResponse getMetrics(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String projectIds,
            @RequestParam String start,
            @RequestParam String end) {

        Instant startTime = Instant.parse(start);
        Instant endTime = Instant.parse(end);
        Duration range = Duration.between(startTime, endTime);

        List<String> projectIdList = null;
        if (projectIds != null && !projectIds.isBlank()) {
            projectIdList = Arrays.stream(projectIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        List<MetricsBucket> buckets;
        if (range.compareTo(FIVE_MIN_THRESHOLD) <= 0) {
            buckets = query5min(projectId, projectIdList, startTime, endTime);
        } else {
            buckets = queryHourly(projectId, projectIdList, startTime, endTime);
        }

        buckets.sort(Comparator.comparing(MetricsBucket::getTime));

        MetricsSummary summary = MetricsSummary.builder()
                .total(buckets.stream().mapToLong(MetricsBucket::getTotal).sum())
                .success(buckets.stream().mapToLong(MetricsBucket::getSuccess).sum())
                .error(buckets.stream().mapToLong(MetricsBucket::getError).sum())
                .canceled(buckets.stream().mapToLong(MetricsBucket::getCanceled).sum())
                .totalDurationMs(buckets.stream().mapToLong(MetricsBucket::getTotalDurationMs).sum())
                .finishedCount(buckets.stream().mapToLong(MetricsBucket::getFinishedCount).sum())
                .build();

        return MetricsResponse.builder()
                .summary(summary)
                .buckets(buckets)
                .build();
    }

    private List<MetricsBucket> query5min(String projectId, List<String> projectIds,
                                           Instant start, Instant end) {
        List<ExecutionMetrics5minEntity> rows;
        if (projectId != null) {
            rows = fiveMinRepository.findByProjectIdAndBucketTimeBetween(projectId, start, end);
        } else if (projectIds != null && !projectIds.isEmpty()) {
            rows = fiveMinRepository.findByProjectIdInAndBucketTimeBetween(projectIds, start, end);
        } else {
            rows = List.of();
        }

        // Merge rows across projects into unified time buckets
        return mergeRows(rows.stream().map(r -> MetricsBucket.builder()
                .time(r.getBucketTime())
                .total(r.getTotalCount())
                .success(r.getSuccessCount())
                .error(r.getErrorCount())
                .canceled(r.getCanceledCount())
                .totalDurationMs(r.getTotalDurationMs())
                .finishedCount(r.getFinishedCount())
                .build()).toList());
    }

    private List<MetricsBucket> queryHourly(String projectId, List<String> projectIds,
                                             Instant start, Instant end) {
        List<ExecutionMetricsHourlyEntity> rows;
        if (projectId != null) {
            rows = hourlyRepository.findByProjectIdAndBucketTimeBetween(projectId, start, end);
        } else if (projectIds != null && !projectIds.isEmpty()) {
            rows = hourlyRepository.findByProjectIdInAndBucketTimeBetween(projectIds, start, end);
        } else {
            rows = List.of();
        }

        return mergeRows(rows.stream().map(r -> MetricsBucket.builder()
                .time(r.getBucketTime())
                .total(r.getTotalCount())
                .success(r.getSuccessCount())
                .error(r.getErrorCount())
                .canceled(r.getCanceledCount())
                .totalDurationMs(r.getTotalDurationMs())
                .finishedCount(r.getFinishedCount())
                .build()).toList());
    }

    /**
     * When querying "all projects", multiple rows can share the same bucket time.
     * Merge them by summing values.
     */
    private List<MetricsBucket> mergeRows(List<MetricsBucket> buckets) {
        var byTime = new java.util.LinkedHashMap<Instant, MetricsBucket>();
        for (MetricsBucket b : buckets) {
            byTime.merge(b.getTime(), b, (existing, incoming) ->
                    MetricsBucket.builder()
                            .time(existing.getTime())
                            .total(existing.getTotal() + incoming.getTotal())
                            .success(existing.getSuccess() + incoming.getSuccess())
                            .error(existing.getError() + incoming.getError())
                            .canceled(existing.getCanceled() + incoming.getCanceled())
                            .totalDurationMs(existing.getTotalDurationMs() + incoming.getTotalDurationMs())
                            .finishedCount(existing.getFinishedCount() + incoming.getFinishedCount())
                            .build());
        }
        return new ArrayList<>(byTime.values());
    }
}
