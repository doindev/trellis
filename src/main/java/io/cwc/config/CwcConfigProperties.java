package io.cwc.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parses cwc.config.paths and cwc.config.mode properties for the config bootstrap system.
 */
@Slf4j
@Component
@Getter
public class CwcConfigProperties {

    @Value("${cwc.config.paths:.cwc}")
    private String rawPaths;

    @Value("${cwc.config.mode:seed}")
    private String rawMode;

    @Value("${cwc.config.writeback:false}")
    private boolean writeback;

    private List<Path> configPaths;
    private ConfigMode configMode;

    public enum ConfigMode {
        SEED, SYNC
    }

    @PostConstruct
    void init() {
        // Parse mode
        configMode = "sync".equalsIgnoreCase(rawMode.trim()) ? ConfigMode.SYNC : ConfigMode.SEED;

        // Parse paths
        List<Path> paths = new ArrayList<>();
        if (rawPaths != null && !rawPaths.isBlank()) {
            for (String segment : rawPaths.split(",")) {
                String trimmed = segment.trim();
                if (!trimmed.isEmpty()) {
                    Path path = Path.of(trimmed);
                    if (Files.isDirectory(path)) {
                        paths.add(path);
                    } else {
                        log.warn("Config path does not exist or is not a directory: {}", trimmed);
                    }
                }
            }
        }
        configPaths = Collections.unmodifiableList(paths);

        if (!configPaths.isEmpty()) {
            log.info("Config bootstrap enabled: mode={}, paths={}", configMode, configPaths);
        }
    }

    /**
     * Returns true if config bootstrap is enabled (at least one valid path configured).
     */
    public boolean isEnabled() {
        return !configPaths.isEmpty();
    }

    /**
     * Returns true if filesystem writeback is enabled.
     */
    public boolean isWritebackEnabled() {
        return writeback && getWritablePath().isPresent();
    }

    /**
     * Returns the first config path as the writable target for filesystem writeback.
     */
    public Optional<Path> getWritablePath() {
        return configPaths.isEmpty() ? Optional.empty() : Optional.of(configPaths.get(0));
    }

    /**
     * Prepends a path to the config paths list if not already present.
     * Used by GitSyncRunner to register the git local path after a successful sync.
     */
    public void prependPath(Path path) {
        if (path == null || !Files.isDirectory(path)) return;
        Path normalized = path.toAbsolutePath().normalize();
        for (Path existing : configPaths) {
            if (existing.toAbsolutePath().normalize().equals(normalized)) return;
        }
        List<Path> updated = new ArrayList<>();
        updated.add(normalized);
        updated.addAll(configPaths);
        configPaths = Collections.unmodifiableList(updated);
        log.info("Registered git sync path for config bootstrap: {}", normalized);
    }
}
