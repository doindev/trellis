package io.trellis.service;

import io.trellis.entity.UserEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

    @Transactional
    public UserEntity createUser(UserEntity user) {
        return userRepository.save(user);
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
