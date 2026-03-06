package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.service.RemoteControlService;

import java.util.Map;

@RestController
@RequestMapping("/api/agent-control")
@RequiredArgsConstructor
public class AgentControlController {

    private final RemoteControlService remoteControlService;

    @PostMapping("/{requestId}/respond")
    public Map<String, Object> respondToRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        remoteControlService.resolveRequest(requestId, approved);
        return Map.of("status", "ok");
    }

    @PostMapping("/revoke")
    public Map<String, Object> revokeSession() {
        remoteControlService.revokeAll();
        return Map.of("status", "revoked");
    }
}
