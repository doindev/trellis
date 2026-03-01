package io.trellis.controller;

import io.trellis.entity.CacheDefinitionEntity;
import io.trellis.exception.BadRequestException;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.CacheDefinitionRepository;
import io.trellis.service.CacheRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/caches")
@RequiredArgsConstructor
public class CacheDefinitionController {

    private final CacheDefinitionRepository repository;
    private final CacheRegistryService cacheRegistryService;

    @GetMapping
    public List<Map<String, Object>> list() {
        List<CacheDefinitionEntity> defs = repository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (CacheDefinitionEntity def : defs) {
            result.add(toResponse(def));
        }
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Cache name is required");
        }
        if (repository.findByName(name).isPresent()) {
            throw new BadRequestException("A cache with name '" + name + "' already exists");
        }

        CacheDefinitionEntity entity = CacheDefinitionEntity.builder()
                .name(name)
                .description((String) body.get("description"))
                .maxSize(toInt(body.get("maxSize"), 1000))
                .ttlSeconds(toLong(body.get("ttlSeconds"), 3600))
                .build();

        entity = repository.save(entity);
        // Pre-create the runtime cache
        cacheRegistryService.getOrCreateCache(entity.getName(), entity.getMaxSize(), entity.getTtlSeconds());
        return toResponse(entity);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        CacheDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cache definition not found"));
        return toResponse(entity);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CacheDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cache definition not found"));

        if (body.containsKey("name")) {
            String newName = (String) body.get("name");
            if (newName != null && !newName.equals(entity.getName())) {
                if (repository.findByName(newName).isPresent()) {
                    throw new BadRequestException("A cache with name '" + newName + "' already exists");
                }
                // Remove old runtime cache, a new one will be created on next use
                cacheRegistryService.removeCache(entity.getName());
                entity.setName(newName);
            }
        }
        if (body.containsKey("description")) {
            entity.setDescription((String) body.get("description"));
        }
        if (body.containsKey("maxSize")) {
            entity.setMaxSize(toInt(body.get("maxSize"), entity.getMaxSize()));
            // Recreate runtime cache with new config
            cacheRegistryService.removeCache(entity.getName());
        }
        if (body.containsKey("ttlSeconds")) {
            entity.setTtlSeconds(toLong(body.get("ttlSeconds"), entity.getTtlSeconds()));
            cacheRegistryService.removeCache(entity.getName());
        }

        entity = repository.save(entity);
        return toResponse(entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        CacheDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cache definition not found"));
        cacheRegistryService.removeCache(entity.getName());
        repository.delete(entity);
    }

    @PostMapping("/{id}/clear")
    public Map<String, Object> clear(@PathVariable String id) {
        CacheDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cache definition not found"));
        cacheRegistryService.clearCache(entity.getName());
        return toResponse(entity);
    }

    @GetMapping("/{id}/stats")
    public Map<String, Object> stats(@PathVariable String id) {
        CacheDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cache definition not found"));
        return cacheRegistryService.getCacheStats(entity.getName());
    }

    private Map<String, Object> toResponse(CacheDefinitionEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("maxSize", entity.getMaxSize());
        map.put("ttlSeconds", entity.getTtlSeconds());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        map.putAll(cacheRegistryService.getCacheStats(entity.getName()));
        return map;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    private long toLong(Object value, long defaultValue) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }
}
