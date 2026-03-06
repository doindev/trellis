package io.cwc.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final WebhookSecurityRegistry webhookSecurityRegistry;

    @Bean
    @Order(1)
    public SecurityFilterChain basicAuthWebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("basicAuth", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> {})
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiKeyWebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("apiKey", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new ApiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain jwtWebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("jwt", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain oauth2WebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("oauth2", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(5)
    public SecurityFilterChain sessionWebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("session", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> {})
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(6)
    public SecurityFilterChain entraWebhookChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new DynamicWebhookRequestMatcher("entra", webhookSecurityRegistry))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(100)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
