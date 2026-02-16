package io.trellis.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CredentialCreateRequest {
    private String name;
    private String type;
    private Map<String, Object> data;
}
