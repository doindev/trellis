package io.trellis.credentials;

import io.trellis.nodes.core.NodeParameter;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CredentialType {
    private String type;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String documentationUrl;
    private String extendsType;
    private List<NodeParameter> properties;
}
