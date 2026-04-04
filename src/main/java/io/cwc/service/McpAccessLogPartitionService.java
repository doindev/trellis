package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Manages date-based partitions for the mcp_access_log table.
 *
 * <p><b>PostgreSQL:</b> Creates the parent table as RANGE-partitioned on log_date.
 * Daily partitions are created ahead of time and old ones are DROPped (instant, no undo log bloat).
 *
 * <p><b>H2 (dev):</b> Partitioning is not supported. Falls back to DELETE for retention,
 * which is acceptable for dev where volume is low.
 *
 * <p>Only one instance in a cluster performs maintenance (guarded by TriggerLockService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration")
@ConditionalOnProperty(name = "cwc.features.mcp-server.enabled", havingValue = "true", matchIfMissing = true)
public class McpAccessLogPartitionService {

    private static final DateTimeFormatter PART_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TABLE = "mcp_access_log";

    private final JdbcTemplate jdbc;
    private final TriggerLockService triggerLockService;

    @Value("${cwc.mcp.access-log.retention-days:90}")
    private int retentionDays;

    private boolean isPostgres;

    @PostConstruct
    void detectDialect() {
        try {
            String url = jdbc.getDataSource().getConnection().getMetaData().getURL();
            isPostgres = url != null && url.contains("postgresql");
        } catch (Exception e) {
            isPostgres = false;
        }

        if (isPostgres) {
            ensurePartitionedTable();
            ensurePartitions(7); // create 7 days ahead
        }
        log.info("MCP access log partition service: dialect={}, retentionDays={}",
                isPostgres ? "PostgreSQL" : "H2/other", retentionDays);
    }

    /**
     * Runs daily at 2 AM. Creates tomorrow's partition and drops expired ones.
     * Only one instance acquires the lock.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void maintain() {
        if (!triggerLockService.tryAcquire("_system", "mcpAccessLogPartition")) {
            log.debug("Skipping MCP access log maintenance — another instance holds the lock");
            return;
        }
        try {
            if (isPostgres) {
                ensurePartitions(7);
                dropExpiredPartitions();
            } else {
                deleteFallback();
            }
        } finally {
            triggerLockService.release("_system", "mcpAccessLogPartition");
        }
    }

    // ── PostgreSQL partition management ──

    /**
     * Convert the Hibernate-created table to a partitioned table if it isn't already.
     * On first run, Hibernate creates a normal table; we replace it with a partitioned one.
     */
    private void ensurePartitionedTable() {
        try {
            // Check if already partitioned
            Boolean partitioned = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = ?::regclass)",
                Boolean.class, TABLE);
            if (Boolean.TRUE.equals(partitioned)) return;

            log.info("Converting {} to a range-partitioned table on log_date", TABLE);

            // Rename the existing table, create partitioned parent, migrate data
            jdbc.execute("ALTER TABLE " + TABLE + " RENAME TO " + TABLE + "_old");
            jdbc.execute("""
                CREATE TABLE %s (
                    id VARCHAR(255) NOT NULL,
                    log_date DATE NOT NULL,
                    ip_address VARCHAR(45) NOT NULL,
                    username VARCHAR(255) NOT NULL,
                    token_name VARCHAR(255),
                    endpoint_path VARCHAR(255) NOT NULL,
                    request_url VARCHAR(2000) NOT NULL,
                    http_method VARCHAR(10) NOT NULL,
                    status VARCHAR(10) NOT NULL,
                    response_code INTEGER,
                    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                    PRIMARY KEY (id, log_date)
                ) PARTITION BY RANGE (log_date)
                """.formatted(TABLE));

            // Create partition for today and copy any existing data
            LocalDate today = LocalDate.now();
            createPartition(today);
            jdbc.execute("INSERT INTO " + TABLE + " SELECT * FROM " + TABLE + "_old");
            jdbc.execute("DROP TABLE " + TABLE + "_old");

        } catch (Exception e) {
            log.warn("Could not convert {} to partitioned table (may already be partitioned or not needed): {}",
                    TABLE, e.getMessage());
        }
    }

    private void ensurePartitions(int daysAhead) {
        LocalDate start = LocalDate.now();
        for (int i = 0; i <= daysAhead; i++) {
            createPartition(start.plusDays(i));
        }
    }

    private void createPartition(LocalDate date) {
        String partName = TABLE + "_" + date.format(PART_FMT);
        LocalDate nextDay = date.plusDays(1);
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s')"
                    .formatted(partName, TABLE, date, nextDay));
        } catch (Exception e) {
            // Partition may already exist
            log.trace("Partition {} already exists or could not be created: {}", partName, e.getMessage());
        }
    }

    private void dropExpiredPartitions() {
        LocalDate cutoff = LocalDate.now().minus(retentionDays, ChronoUnit.DAYS);

        // Find all child partitions
        List<String> partitions = jdbc.queryForList(
            "SELECT inhrelid::regclass::text FROM pg_inherits WHERE inhparent = ?::regclass",
            String.class, TABLE);

        int dropped = 0;
        for (String partName : partitions) {
            // Extract date from partition name: mcp_access_log_20260401
            String datePart = partName.replace(TABLE + "_", "").replace("\"", "");
            try {
                LocalDate partDate = LocalDate.parse(datePart, PART_FMT);
                if (partDate.isBefore(cutoff)) {
                    jdbc.execute("DROP TABLE IF EXISTS " + partName);
                    dropped++;
                    log.info("Dropped expired partition: {} (older than {} day retention)", partName, retentionDays);
                }
            } catch (Exception e) {
                // Not a date-named partition, skip
            }
        }
        if (dropped > 0) {
            log.info("Dropped {} expired MCP access log partitions (cutoff: {})", dropped, cutoff);
        }
    }

    // ── H2 / non-PostgreSQL fallback ──

    private void deleteFallback() {
        LocalDate cutoff = LocalDate.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = jdbc.update("DELETE FROM " + TABLE + " WHERE log_date < ?", cutoff);
        if (deleted > 0) {
            log.info("Deleted {} MCP access log entries older than {} ({} day retention, non-partitioned fallback)",
                    deleted, cutoff, retentionDays);
        }
    }
}
