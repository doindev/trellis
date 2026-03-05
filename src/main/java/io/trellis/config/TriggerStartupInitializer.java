package io.trellis.config;

import io.trellis.engine.TriggerSchedulerService;
import io.trellis.entity.WorkflowEntity;
import io.trellis.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers schedule/polling triggers for all published workflows at application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerStartupInitializer {

    private final WorkflowRepository workflowRepository;
    private final TriggerSchedulerService triggerSchedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        List<WorkflowEntity> published = workflowRepository.findByPublished(true);
        if (!published.isEmpty()) {
            triggerSchedulerService.registerAllPublished(published);
        }
    }
}
