package io.trellis.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CredentialUpdateRequest {
    private String name;
    private Map<String, Object> data;
}
