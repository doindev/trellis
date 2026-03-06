package io.cwc.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebhookSecurityRegistry {

    @Data
    public static class WebhookPattern {
        private final String method;
        private final String path;

        public String toKey() {
            return method.toUpperCase() + ":" + path;
        }
    }

    private final Map<String, Set<WebhookPattern>> chainPatterns = new ConcurrentHashMap<>();
    private final Map<String, String> reverseIndex = new ConcurrentHashMap<>();

    public synchronized void register(String chainName, String method, String path) {
        WebhookPattern pattern = new WebhookPattern(method.toUpperCase(), path);
        String key = pattern.toKey();

        String existingChain = reverseIndex.get(key);
        if (existingChain != null && !existingChain.equals(chainName)) {
            deregister(existingChain, method, path);
        }

        chainPatterns.computeIfAbsent(chainName, k -> ConcurrentHashMap.newKeySet()).add(pattern);
        reverseIndex.put(key, chainName);
        log.debug("Registered webhook pattern {} -> chain '{}'", key, chainName);
    }

    public synchronized void deregister(String chainName, String method, String path) {
        WebhookPattern pattern = new WebhookPattern(method.toUpperCase(), path);
        String key = pattern.toKey();

        Set<WebhookPattern> patterns = chainPatterns.get(chainName);
        if (patterns != null) {
            patterns.removeIf(p -> p.toKey().equals(key));
        }
        reverseIndex.remove(key);
    }

    public synchronized void deregisterAll(String chainName) {
        Set<WebhookPattern> patterns = chainPatterns.remove(chainName);
        if (patterns != null) {
            for (WebhookPattern pattern : patterns) {
                reverseIndex.remove(pattern.toKey());
            }
        }
    }

    public boolean matches(String chainName, String method, String path) {
        String key = method.toUpperCase() + ":" + path;
        String registeredChain = reverseIndex.get(key);
        return chainName.equals(registeredChain);
    }

    public String getChainForWebhook(String method, String path) {
        String key = method.toUpperCase() + ":" + path;
        return reverseIndex.getOrDefault(key, "none");
    }

    public synchronized void clearAll() {
        chainPatterns.clear();
        reverseIndex.clear();
    }
}
