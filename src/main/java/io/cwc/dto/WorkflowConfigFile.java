package io.cwc.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Deserialization target for workflow JSON files.
 * Extends the existing frontend export format with configId and published fields.
 */
@Data
public class WorkflowConfigFile {

    private String configId;
    private String name;
    private String description;
    private String type;
    private Boolean published;
    private String icon;

    private List<Map<String, Object>> nodes;
    private Map<String, Object> connections;
    private Map<String, Object> settings;

    private Boolean mcpEnabled;
    private String mcpDescription;
    private Object mcpInputSchema;
    private Object mcpOutputSchema;
    private Boolean swaggerEnabled;
}
