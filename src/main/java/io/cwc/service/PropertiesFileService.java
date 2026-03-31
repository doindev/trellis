package io.cwc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes credential properties to the appropriate Spring application properties file.
 *
 * Target file:
 * - Single active profile (e.g. "local") → src/main/resources/application-local.properties (or .yaml)
 * - No profile / default → src/main/resources/application.properties (or .yaml)
 *
 * A property is only added if it does not already exist in EITHER the base
 * application.properties OR the profile-specific file.
 */
@Slf4j
@Service
public class PropertiesFileService {

    private static final Path SRC_RESOURCES = Path.of("src", "main", "resources");

    private final Environment environment;
    private final ReentrantLock writeLock = new ReentrantLock();

    public PropertiesFileService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Adds properties only if they do not already exist in the base or profile properties file.
     */
    public void addPropertiesIfAbsent(Map<String, String> properties) {
        if (properties.isEmpty()) return;

        writeLock.lock();
        try {
            Path targetFile = resolveTargetFile();
            Path baseFile = resolveBaseFile();

            // Load existing keys from both base and profile files
            Properties baseProps = loadProperties(baseFile);
            Properties profileProps = baseFile.equals(targetFile) ? baseProps : loadProperties(targetFile);

            List<String> newKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String key = entry.getKey();
                if (!baseProps.containsKey(key) && !profileProps.containsKey(key)) {
                    newKeys.add(key);
                }
            }

            if (newKeys.isEmpty()) return;

            Collections.sort(newKeys);
            Files.createDirectories(targetFile.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                writer.newLine();
                writer.write("# CWC credential properties (auto-generated)");
                writer.newLine();
                for (String key : newKeys) {
                    writer.write(key + "=" + properties.get(key));
                    writer.newLine();
                }
            }
            log.info("Added {} credential properties to {}", newKeys.size(), targetFile);
        } catch (IOException e) {
            log.error("Failed to write properties: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Adds a single property only if it does not already exist.
     */
    public void addPropertyIfAbsent(String key, String value) {
        addPropertiesIfAbsent(Map.of(key, value));
    }

    /**
     * Resolves the target properties file based on active Spring profiles.
     * Single profile → application-{profile}.properties (or .yaml)
     * Otherwise → application.properties (or .yaml)
     */
    private Path resolveTargetFile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 1 && !"default".equals(activeProfiles[0])) {
            String profile = activeProfiles[0];
            // Check .yaml first, then .properties
            Path yaml = SRC_RESOURCES.resolve("application-" + profile + ".yaml");
            if (Files.isRegularFile(yaml)) return yaml;
            Path yml = SRC_RESOURCES.resolve("application-" + profile + ".yml");
            if (Files.isRegularFile(yml)) return yml;
            // Default to .properties (create if needed)
            return SRC_RESOURCES.resolve("application-" + profile + ".properties");
        }
        return resolveBaseFile();
    }

    /**
     * Resolves the base application properties file (application.properties or .yaml).
     */
    private Path resolveBaseFile() {
        Path yaml = SRC_RESOURCES.resolve("application.yaml");
        if (Files.isRegularFile(yaml)) return yaml;
        Path yml = SRC_RESOURCES.resolve("application.yml");
        if (Files.isRegularFile(yml)) return yml;
        return SRC_RESOURCES.resolve("application.properties");
    }

    private Properties loadProperties(Path path) {
        Properties props = new Properties();
        if (!Files.isRegularFile(path)) return props;

        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            // For YAML, do a simple line-based key scan (key: value or key=value)
            return loadYamlKeysSimple(path);
        }

        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
        } catch (IOException e) {
            log.warn("Failed to load {}: {}", path, e.getMessage());
        }
        return props;
    }

    /**
     * Simple line-based scan of a YAML file to extract top-level-ish keys.
     * Not a full YAML parser — just checks for lines like "KEY: value" or "KEY=value".
     */
    private Properties loadYamlKeysSimple(Path path) {
        Properties props = new Properties();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int colonIdx = trimmed.indexOf(':');
                int equalsIdx = trimmed.indexOf('=');
                int sep = -1;
                if (colonIdx > 0 && (equalsIdx < 0 || colonIdx < equalsIdx)) sep = colonIdx;
                else if (equalsIdx > 0) sep = equalsIdx;
                if (sep > 0) {
                    String key = trimmed.substring(0, sep).trim();
                    String value = trimmed.substring(sep + 1).trim();
                    props.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan YAML keys from {}: {}", path, e.getMessage());
        }
        return props;
    }
}
