package io.cwc.security;

import io.cwc.entity.McpApiTokenEntity;
import io.cwc.entity.UserEntity;
import io.cwc.repository.UserRepository;
import io.cwc.service.McpApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Intercepts all /mcp/** requests to enforce API key authentication
 * on endpoints that have apiKeyRequired=true. Logs every access attempt.
 */
@Slf4j
@Component
@Order(1) // Run early, before other filters
@RequiredArgsConstructor
@ConditionalOnBean(McpApiTokenService.class)
public class McpApiKeyFilter extends OncePerRequestFilter {

    private final McpApiTokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter /mcp/ paths, not /api/mcp-tokens or other API routes
        return !path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String mcpPath = extractMcpPath(uri);

        // Check if this endpoint requires API key
        if (!tokenService.isApiKeyRequired(mcpPath)) {
            // No auth required — log and pass through
            tokenService.logAccess(request, mcpPath, "anonymous", null, "ALLOWED", 200);
            filterChain.doFilter(request, response);
            return;
        }

        // Token management page is always accessible (needs auth but different flow)
        if (uri.endsWith("/token-management")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate the API token
        Optional<McpApiTokenEntity> tokenOpt = tokenService.validateRequest(request);

        if (tokenOpt.isEmpty()) {
            String reason = request.getHeader("Authorization") != null ? "DENIED" : "DENIED";
            tokenService.logAccess(request, mcpPath, "anonymous", null, reason, 401);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Valid API token required. "
                    + "Visit " + uri.replaceAll("/sse$", "") + "/token-management to create one.\"}");
            return;
        }

        McpApiTokenEntity token = tokenOpt.get();
        String username = userRepository.findById(token.getUserId())
                .map(UserEntity::getEmail)
                .orElse("unknown");

        tokenService.logAccess(request, mcpPath, username, token.getName(), "ALLOWED", 200);
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the MCP endpoint path from the request URI.
     * /mcp/agent/sse → agent
     * /mcp/project-abc/tools → project-abc/tools
     */
    private static String extractMcpPath(String uri) {
        String path = uri.substring("/mcp/".length());
        // Strip trailing /sse for SSE connections
        if (path.endsWith("/sse")) {
            path = path.substring(0, path.length() - 4);
        }
        // Strip /token-management
        if (path.endsWith("/token-management")) {
            path = path.substring(0, path.length() - "/token-management".length());
        }
        return path;
    }
}
