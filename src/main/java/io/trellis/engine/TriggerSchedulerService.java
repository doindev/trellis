package io.trellis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.entity.WorkflowEntity;
import io.trellis.nodes.core.NodeRegistry;
import io.trellis.service.TriggerLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates schedule/polling trigger activation for published workflows.
 * Bridges SchedulerService (task scheduling), TriggerLockService (distributed locking),
 * and WorkflowEngine (execution creation).
 */
@Slf4j
@Service
public class TriggerSchedulerService {

    private final SchedulerService schedulerService;
    private final TriggerLockService triggerLockService;
    private final WorkflowEngine workflowEngine;
    private final NodeRegistry nodeRegistry;
    private final ObjectMapper objectMapper;

    public TriggerSchedulerService(SchedulerService schedulerService,
                                    TriggerLockService triggerLockService,
                                    @Lazy WorkflowEngine workflowEngine,
                                    NodeRegistry nodeRegistry,
                                    ObjectMapper objectMapper) {
        this.schedulerService = schedulerService;
        this.triggerLockService = triggerLockService;
        this.workflowEngine = workflowEngine;
        this.nodeRegistry = nodeRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Scans the workflow's nodes for schedule and polling triggers and registers
     * them with the SchedulerService.
     */
    @SuppressWarnings("unchecked")
    public void registerWorkflowTriggers(WorkflowEntity workflow) {
        String workflowId = workflow.getId();
        deregisterWorkflowTriggers(workflowId);

        Object nodesObj = workflow.getNodes();
        if (nodesObj == null) return;

        List<Map<String, Object>> nodes;
        if (nodesObj instanceof List) {
            nodes = (List<Map<String, Object>>) nodesObj;
        } else if (nodesObj instanceof String) {
            try {
                nodes = objectMapper.readValue((String) nodesObj,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                log.error("Failed to parse workflow nodes for trigger registration", e);
                return;
            }
        } else {
            return;
        }

        for (Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            String nodeId = (String) node.get("id");
            if (type == null || nodeId == null) continue;

            var regOpt = nodeRegistry.getNode(type);
            if (regOpt.isEmpty()) continue;
            var reg = regOpt.get();

            Map<String, Object> params = (Map<String, Object>) node.getOrDefault("parameters", Map.of());

            if ("scheduleTrigger".equals(type)) {
                registerScheduleTrigger(workflowId, nodeId, params);
            } else if (reg.isPolling()) {
                registerPollingTrigger(workflowId, nodeId, params);
            }
        }
    }

    private void registerScheduleTrigger(String workflowId, String nodeId, Map<String, Object> params) {
        String taskKey = workflowId + "_" + nodeId;
        String rule = (String) params.getOrDefault("rule", "interval");
        boolean instanceSync = !Boolean.FALSE.equals(params.get("instanceSync"));

        Runnable task = buildTriggerRunnable(workflowId, nodeId, instanceSync);

        if ("cronExpression".equals(rule)) {
            String cron = (String) params.getOrDefault("cronExpression", "0 0 * * *");
            String springCron = convertToSpringCron(cron);
            schedulerService.registerCron(taskKey, springCron, task);
            log.info("Registered schedule trigger (cron={}) for workflow={} node={} instanceSync={}",
                    cron, workflowId, nodeId, instanceSync);
        } else {
            int interval = toInt(params.getOrDefault("interval", 60), 60);
            schedulerService.registerInterval(taskKey, interval, task);
            log.info("Registered schedule trigger (interval={}s) for workflow={} node={} instanceSync={}",
                    interval, workflowId, nodeId, instanceSync);
        }
    }

    private void registerPollingTrigger(String workflowId, String nodeId, Map<String, Object> params) {
        String taskKey = workflowId + "_" + nodeId;
        int pollInterval = toInt(params.getOrDefault("pollInterval", 60), 60);
        boolean instanceSync = !Boolean.FALSE.equals(params.get("instanceSync"));

        Runnable task = buildTriggerRunnable(workflowId, nodeId, instanceSync);

        schedulerService.registerInterval(taskKey, pollInterval, task);
        log.info("Registered polling trigger (interval={}s) for workflow={} node={} instanceSync={}",
                pollInterval, workflowId, nodeId, instanceSync);
    }

    private Runnable buildTriggerRunnable(String workflowId, String nodeId, boolean instanceSync) {
        return () -> {
            try {
                if (instanceSync) {
                    if (!triggerLockService.tryAcquire(workflowId, nodeId)) {
                        log.debug("Skipping trigger (lock held by another instance): workflow={} node={}",
                                workflowId, nodeId);
                        return;
                    }
                }
                String executionId = workflowEngine.startTriggerExecution(workflowId, nodeId);
                log.debug("Trigger fired: workflow={} node={} execution={}", workflowId, nodeId, executionId);
            } catch (Exception e) {
                log.error("Trigger execution failed: workflow={} node={}", workflowId, nodeId, e);
            }
        };
    }

    /**
     * Deregisters all scheduled triggers for a workflow and releases any held locks.
     */
    public void deregisterWorkflowTriggers(String workflowId) {
        schedulerService.deregisterByPrefix(workflowId + "_");
        triggerLockService.releaseAllForWorkflow(workflowId);
    }

    /**
     * Registers triggers for all given published workflows. Used at startup.
     */
    public void registerAllPublished(List<WorkflowEntity> workflows) {
        for (WorkflowEntity workflow : workflows) {
            try {
                registerWorkflowTriggers(workflow);
            } catch (Exception e) {
                log.error("Failed to register triggers for workflow {}: {}", workflow.getId(), e.getMessage());
            }
        }
        log.info("Registered triggers for {} published workflow(s)", workflows.size());
    }

    /**
     * Periodic heartbeat + expired lock cleanup.
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeatAndCleanup() {
        triggerLockService.heartbeat();
        triggerLockService.cleanupExpired();
    }

    /**
     * Converts a 5-field Unix cron (min hour dom mon dow) to a 6-field Spring cron
     * (sec min hour dom mon dow) by prepending "0 ".
     */
    private String convertToSpringCron(String unixCron) {
        if (unixCron == null || unixCron.isBlank()) return "0 0 0 * * *";
        String trimmed = unixCron.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
