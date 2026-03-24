package io.cwc.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.cwc.dto.ConfigReloadResult;
import io.cwc.service.ConfigBootstrapService;

/**
 * Reports config bootstrap errors in the health endpoint.
 * Does not fail the pod — reports DOWN only if there are unresolved errors.
 */
@Component("configBootstrap")
@RequiredArgsConstructor
public class ConfigBootstrapHealthIndicator implements HealthIndicator {

    private final ConfigBootstrapService configBootstrapService;

    @Override
    public Health health() {
        ConfigReloadResult result = configBootstrapService.getLastResult();

        if (result == null) {
            return Health.unknown().withDetail("reason", "config bootstrap has not run").build();
        }

        Health.Builder builder = result.getErrors().isEmpty() ? Health.up() : Health.down();
        builder.withDetail("mode", result.getMode())
                .withDetail("pathsScanned", result.getPathsScanned())
                .withDetail("projectsCreated", result.getProjectsCreated())
                .withDetail("projectsUpdated", result.getProjectsUpdated())
                .withDetail("workflowsCreated", result.getWorkflowsCreated())
                .withDetail("workflowsUpdated", result.getWorkflowsUpdated());

        if (!result.getErrors().isEmpty()) {
            builder.withDetail("errors", result.getErrors());
        }
        if (!result.getWarnings().isEmpty()) {
            builder.withDetail("warnings", result.getWarnings());
        }

        return builder.build();
    }
}
