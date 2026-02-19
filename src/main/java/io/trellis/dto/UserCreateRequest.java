package io.trellis.dto;

import lombok.Data;

@Data
public class UserCreateRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String role;
}
