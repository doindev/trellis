package io.cwc.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.cwc.entity.UserEntity;
import io.cwc.exception.UnauthenticatedException;
import io.cwc.repository.UserRepository;

@Component
public class SecurityContextHelper {

    private final UserRepository userRepository;
    private final String defaultUserEmail;

    public SecurityContextHelper(UserRepository userRepository,
                                 @Value("${cwc.default-user-email:}") String defaultUserEmail) {
        this.userRepository = userRepository;
        this.defaultUserEmail = defaultUserEmail;
    }

    public UserEntity getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new UnauthenticatedException("User not found: " + auth.getName()));
        }

        if (defaultUserEmail != null && !defaultUserEmail.isBlank()) {
            return userRepository.findByEmail(defaultUserEmail)
                    .orElseThrow(() -> new UnauthenticatedException("Default user not found: " + defaultUserEmail));
        }

        throw new UnauthenticatedException("No authenticated user");
    }

    public String getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
