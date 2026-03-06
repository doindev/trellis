package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MetricsResponse {
    private MetricsSummary summary;
    private List<MetricsBucket> buckets;

    @Data
    @Builder
    public static class MetricsSummary {
        private long total;
        private long success;
        private long error;
        private long canceled;
        private long totalDurationMs;
        private long finishedCount;
    }

    @Data
    @Builder
    public static class MetricsBucket {
        private Instant time;
        private int total;
        private int success;
        private int error;
        private int canceled;
        private long totalDurationMs;
        private int finishedCount;
    }
}
