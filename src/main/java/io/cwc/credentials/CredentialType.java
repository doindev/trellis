package io.cwc.credentials;

import lombok.Builder;
import lombok.Data;

import java.util.List;

import io.cwc.nodes.core.NodeParameter;

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
