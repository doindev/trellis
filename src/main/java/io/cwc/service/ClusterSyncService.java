package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.config.ProjectContextPathFilter;
import io.cwc.engine.TriggerSchedulerService;
import io.cwc.entity.ClusterSyncEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.ClusterSyncRepository;
import io.cwc.repository.WorkflowRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cluster-wide synchronization of in-memory state.
 * Uses version counters in the database to detect changes made by other instances.
 *
 * Tracked domains:
 * - "webhooks" — WebhookSecurityRegistry patterns
 * - "context_paths" — ProjectContextPathFilter cache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterSyncService {

    public static final String DOMAIN_WEBHOOKS = "webhooks";
    public static final String DOMAIN_CONTEXT_PATHS = "context_paths";
    public static final String DOMAIN_TRIGGERS = "triggers";

    private final ClusterSyncRepository repository;
    private final WebhookService webhookService;
    private final ProjectContextPathFilter contextPathFilter;
    private final WorkflowRepository workflowRepository;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private TriggerSchedulerService triggerSchedulerService;

    private final Map<String, Long> localVersions = new ConcurrentHashMap<>();

    /**
     * Bumps the version counter for a domain, signaling to other cluster instances
     * that the corresponding in-memory state needs refreshing.
     */
    @Transactional
    public void notifyChange(String domain) {
        ClusterSyncEntity entity = repository.findById(domain)
                .orElse(new ClusterSyncEntity(domain, 0, Instant.now()));
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        localVersions.put(domain, entity.getVersion());
        log.debug("Cluster sync: bumped {} to version {}", domain, entity.getVersion());
    }

    /**
     * Polls the database for version changes and triggers refreshes on the
     * appropriate components when a change is detected from another instance.
     */
    @Scheduled(fixedDelay = 10_000)
    public void poll() {
        for (ClusterSyncEntity entity : repository.findAll()) {
            String domain = entity.getDomain();
            long dbVersion = entity.getVersion();
            long localVersion = localVersions.getOrDefault(domain, -1L);

            if (dbVersion != localVersion) {
                log.info("Cluster sync: {} changed (db={}, local={}), refreshing",
                        domain, dbVersion, localVersion);
                localVersions.put(domain, dbVersion);
                triggerRefresh(domain);
            }
        }
    }

    private void triggerRefresh(String domain) {
        switch (domain) {
            case DOMAIN_WEBHOOKS -> webhookService.refreshSecurityRegistry();
            case DOMAIN_CONTEXT_PATHS -> contextPathFilter.refreshCache();
            case DOMAIN_TRIGGERS -> refreshTriggers();
            default -> log.warn("Unknown cluster sync domain: {}", domain);
        }
    }

    private void refreshTriggers() {
        if (triggerSchedulerService == null) return;
        List<WorkflowEntity> published = workflowRepository.findByPublished(true);
        triggerSchedulerService.registerAllPublished(published);
        log.info("Refreshed triggers for {} published workflow(s)", published.size());
    }
}
