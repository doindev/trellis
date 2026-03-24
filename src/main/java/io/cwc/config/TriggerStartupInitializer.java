package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.cwc.engine.TriggerSchedulerService;
import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.WorkflowRepository;

import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Registers schedule/polling triggers for all published workflows at application startup.
 * Runs after ExecutionRecoveryInitializer (Order 1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerStartupInitializer {

    private final WorkflowRepository workflowRepository;
    private final TriggerSchedulerService triggerSchedulerService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void onApplicationReady() {
        List<WorkflowEntity> published = workflowRepository.findByPublished(true);
        if (!published.isEmpty()) {
            triggerSchedulerService.registerAllPublished(published);
        }
    }
}
