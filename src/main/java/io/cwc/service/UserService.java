package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.entity.UserEntity;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProjectService projectService;

    public UserService(UserRepository userRepository, @Lazy ProjectService projectService) {
        this.userRepository = userRepository;
        this.projectService = projectService;
    }

    public List<UserEntity> listUsers() {
        return userRepository.findAll();
    }

    public UserEntity getUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<UserEntity> searchUsers(String term) {
        return userRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                term, term, term);
    }

    @Transactional
    public UserEntity createUser(UserEntity user) {
        UserEntity saved = userRepository.save(user);
        projectService.createPersonalProject(saved.getId());
        log.info("Created user {} with personal project", saved.getEmail());
        return saved;
    }

    @Transactional
    public UserEntity updateUser(String id, UserEntity updated) {
        UserEntity existing = getUser(id);
        if (updated.getEmail() != null) existing.setEmail(updated.getEmail());
        if (updated.getFirstName() != null) existing.setFirstName(updated.getFirstName());
        if (updated.getLastName() != null) existing.setLastName(updated.getLastName());
        if (updated.getPasswordHash() != null) existing.setPasswordHash(updated.getPasswordHash());
        if (updated.getRole() != null) existing.setRole(updated.getRole());
        return userRepository.save(existing);
    }

    @Transactional
    public void deleteUser(String id) {
        UserEntity user = getUser(id);
        userRepository.delete(user);
    }
}
