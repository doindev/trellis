package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class TagResponse {
    private String id;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;
}
