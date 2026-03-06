package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.UserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
    List<UserEntity> findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String email, String firstName, String lastName);
}
