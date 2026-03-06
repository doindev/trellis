package io.cwc.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CredentialTestRequest {
    private String type;
    private Map<String, Object> data;
}
