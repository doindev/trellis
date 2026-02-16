package io.trellis.config;

import io.trellis.entity.UserEntity;
import io.trellis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            UserEntity owner = UserEntity.builder()
                    .email("owner@trellis.local")
                    .firstName("Default")
                    .lastName("Owner")
                    .passwordHash("$placeholder$")
                    .role("owner")
                    .build();
            userRepository.save(owner);
            log.info("Seeded default owner user: {}", owner.getEmail());
        }
    }
}
