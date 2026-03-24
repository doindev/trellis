package io.cwc.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.cwc.service.ConfigBootstrapService;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom readiness indicator that checks:
 * 1. Database connectivity
 * 2. Config bootstrap completion (if configured)
 */
@Component("cwcReadiness")
@RequiredArgsConstructor
public class CwcReadinessIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final ConfigBootstrapService configBootstrapService;

    @Override
    public Health health() {
        // Check database connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                return Health.down().withDetail("reason", "database connection invalid").build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("reason", "database unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }

        // Check config bootstrap completion (if paths are configured)
        if (!configBootstrapService.isComplete()) {
            return Health.down().withDetail("reason", "config bootstrap in progress").build();
        }

        return Health.up().build();
    }
}
