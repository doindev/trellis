package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.UserCreateRequest;
import io.cwc.dto.UserResponse;
import io.cwc.dto.UserUpdateRequest;
import io.cwc.entity.UserEntity;
import io.cwc.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String search) {
        var users = (search != null && !search.isBlank())
                ? userService.searchUsers(search)
                : userService.listUsers();
        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return toResponse(userService.getUser(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UserCreateRequest request) {
        UserEntity entity = UserEntity.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(hashPassword(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : "editor")
                .build();
        return toResponse(userService.createUser(entity));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        UserEntity updates = new UserEntity();
        updates.setEmail(request.getEmail());
        updates.setFirstName(request.getFirstName());
        updates.setLastName(request.getLastName());
        updates.setRole(request.getRole());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            updates.setPasswordHash(hashPassword(request.getPassword()));
        }
        return toResponse(userService.updateUser(id, updates));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        userService.deleteUser(id);
    }

    private UserResponse toResponse(UserEntity entity) {
        return UserResponse.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .role(entity.getRole())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String hashPassword(String password) {
        // Simple hash for now; in production use BCrypt
        return String.valueOf(password.hashCode());
    }
}
