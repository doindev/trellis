package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class UserResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Instant createdAt;
    private Instant updatedAt;
}
