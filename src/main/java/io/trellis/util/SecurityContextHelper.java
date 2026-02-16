package io.trellis.util;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityContextHelper {
//    private final UserService userService;
//
//    /**
//     * Returns the currently authenticated user.
//     * Until security context is fully wired, falls back to the first user in the database (dev mode).
//     */
//    public User getCurrentUser() {
//        // TODO: Replace with SecurityContextHolder-based lookup when auth is wired
//        var users = userService.findAll();
//        if (users.isEmpty()) {
//            throw new org.me.trellis.exception.UnauthenticatedException("No authenticated user");
//        }
//        return users.get(0);
//    }
}
