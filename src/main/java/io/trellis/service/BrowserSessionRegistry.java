package io.trellis.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BrowserSessionRegistry {

    private final ConcurrentHashMap<String, BrowserSessionInfo> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> wsSessionToBrowser = new ConcurrentHashMap<>();

    public void registerSession(String browserSessionId, String wsSessionId) {
        sessions.put(browserSessionId, new BrowserSessionInfo(browserSessionId, wsSessionId, Instant.now()));
        wsSessionToBrowser.put(wsSessionId, browserSessionId);
        log.info("Browser session registered: {} (ws: {})", browserSessionId, wsSessionId);
    }

    public void unregisterByWebSocketSession(String wsSessionId) {
        String browserSessionId = wsSessionToBrowser.remove(wsSessionId);
        if (browserSessionId != null) {
            sessions.remove(browserSessionId);
            log.info("Browser session unregistered: {} (ws: {})", browserSessionId, wsSessionId);
        }
    }

    public List<BrowserSessionInfo> getActiveSessions() {
        return new ArrayList<>(sessions.values());
    }

    public Optional<BrowserSessionInfo> getSession(String browserSessionId) {
        return Optional.ofNullable(sessions.get(browserSessionId));
    }

    public int getActiveCount() {
        return sessions.size();
    }

    @Data
    public static class BrowserSessionInfo {
        private final String browserSessionId;
        private final String wsSessionId;
        private final Instant connectedAt;
    }
}
