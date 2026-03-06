package io.cwc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.cwc.entity.CacheDefinitionEntity;
import io.cwc.repository.CacheDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheRegistryService {

    private final ConcurrentHashMap<String, Cache<String, String>> caches = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final CacheDefinitionRepository cacheDefinitionRepository;

    public Cache<String, String> getOrCreateCache(String name, int maxSize, long ttlSeconds) {
        return caches.computeIfAbsent(name, k ->
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .recordStats()
                .build()
        );
    }

    public Cache<String, String> getOrCreateFromDefinition(String name) {
        Cache<String, String> existing = caches.get(name);
        if (existing != null) return existing;

        Optional<CacheDefinitionEntity> defOpt = cacheDefinitionRepository.findByName(name);
        if (defOpt.isPresent()) {
            CacheDefinitionEntity def = defOpt.get();
            return getOrCreateCache(name, def.getMaxSize(), def.getTtlSeconds());
        }
        // Fallback defaults
        return getOrCreateCache(name, 1000, 3600);
    }

    public Optional<Map<String, Object>> lookup(String cacheName, String key) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache == null) return Optional.empty();

        String json = cache.getIfPresent(key);
        if (json == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached value for key '{}' in cache '{}'", key, cacheName, e);
            return Optional.empty();
        }
    }

    public Optional<List<Map<String, Object>>> lookupItems(String cacheName, String key) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache == null) return Optional.empty();

        String json = cache.getIfPresent(key);
        if (json == null) return Optional.empty();

        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            return Optional.of(items);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached items for key '{}' in cache '{}'", key, cacheName, e);
            return Optional.empty();
        }
    }

    public void store(String cacheName, String key, Map<String, Object> value) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache == null) return;

        try {
            cache.put(key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value for key '{}' in cache '{}'", key, cacheName, e);
        }
    }

    public void storeItems(String cacheName, String key, List<Map<String, Object>> items) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache == null) return;

        try {
            cache.put(key, objectMapper.writeValueAsString(items));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize items for key '{}' in cache '{}'", key, cacheName, e);
        }
    }

    public void invalidate(String cacheName, String key) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    public void clearCache(String cacheName) {
        Cache<String, String> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    public void removeCache(String cacheName) {
        Cache<String, String> removed = caches.remove(cacheName);
        if (removed != null) {
            removed.invalidateAll();
        }
    }

    public Map<String, Object> getCacheStats(String cacheName) {
        Cache<String, String> cache = caches.get(cacheName);
        Map<String, Object> stats = new LinkedHashMap<>();
        if (cache != null) {
            var s = cache.stats();
            stats.put("estimatedSize", cache.estimatedSize());
            stats.put("hitCount", s.hitCount());
            stats.put("missCount", s.missCount());
            stats.put("hitRate", s.hitRate());
        } else {
            stats.put("estimatedSize", 0);
            stats.put("hitCount", 0);
            stats.put("missCount", 0);
            stats.put("hitRate", 0.0);
        }
        return stats;
    }

    public boolean isDefinedCacheName(String name) {
        return cacheDefinitionRepository.findByName(name).isPresent();
    }

    public Set<String> listCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
