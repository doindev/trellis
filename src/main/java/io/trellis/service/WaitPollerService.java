package io.trellis.service;

import io.trellis.entity.WaitEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitPollerService {

    private final WaitService waitService;

    // Lazily injected to avoid circular dependency — set by WorkflowEngine after construction
    private volatile ResumeHandler resumeHandler;

    @FunctionalInterface
    public interface ResumeHandler {
        void resumeFromWait(String executionId, String waitNodeId,
                            Map<String, Object> resumeData, Object checkpointState);
    }

    public void setResumeHandler(ResumeHandler handler) {
        this.resumeHandler = handler;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollExpiredWaits() {
        if (resumeHandler == null) return;

        List<WaitEntity> expired = waitService.findExpiredWaits();
        for (WaitEntity wait : expired) {
            try {
                WaitEntity claimed = waitService.resume(wait.getExecutionId(), wait.getNodeId());
                if (claimed != null) {
                    log.info("Auto-resuming expired wait: executionId={}, nodeId={}, type={}",
                            wait.getExecutionId(), wait.getNodeId(), wait.getWaitType());
                    resumeHandler.resumeFromWait(
                            wait.getExecutionId(),
                            wait.getNodeId(),
                            Map.of("_timedResume", true),
                            claimed.getExecutionState());
                }
            } catch (Exception e) {
                log.error("Failed to auto-resume wait: executionId={}, nodeId={}",
                        wait.getExecutionId(), wait.getNodeId(), e);
            }
        }
    }
}
