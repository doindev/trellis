package io.cwc.dto;

import lombok.Data;
import java.util.List;

/**
 * Deserialization target for manifest.json in config directories.
 */
@Data
public class ManifestFile {

    private String version;
    private String settings;
    private List<ManifestProject> projects;

    @Data
    public static class ManifestProject {
        private String config;
        private List<String> workflows;
    }
}
