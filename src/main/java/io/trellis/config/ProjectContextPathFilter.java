package io.trellis.config;

import io.trellis.repository.ProjectRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectContextPathFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "api", "webhook", "webhook-test", "h2-console"
    );

    private final ProjectRepository projectRepository;

    private volatile Set<String> contextPaths = Set.of();

    @PostConstruct
    public void refreshCache() {
        this.contextPaths = projectRepository.findByContextPathIsNotNull().stream()
                .map(p -> p.getContextPath())
                .collect(Collectors.toSet());
        log.debug("Refreshed context path cache: {}", contextPaths);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Parse first path segment
        String path = uri.startsWith("/") ? uri.substring(1) : uri;
        if (path.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        int slashIdx = path.indexOf('/');
        String firstSegment = slashIdx >= 0 ? path.substring(0, slashIdx) : path;
        String remaining = slashIdx >= 0 ? path.substring(slashIdx + 1) : "";

        // Skip known prefixes
        if (SKIP_PREFIXES.contains(firstSegment)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if first segment is a known context path → forward to /webhook/
        if (contextPaths.contains(firstSegment)) {
            String forwardPath = "/webhook/" + firstSegment + (remaining.isEmpty() ? "" : "/" + remaining);
            request.getRequestDispatcher(forwardPath).forward(request, response);
            return;
        }

        // Check if first segment ends with "-test" and the prefix is a known context path
        if (firstSegment.endsWith("-test")) {
            String prefix = firstSegment.substring(0, firstSegment.length() - "-test".length());
            if (contextPaths.contains(prefix)) {
                String forwardPath = "/webhook-test/" + prefix + (remaining.isEmpty() ? "" : "/" + remaining);
                request.getRequestDispatcher(forwardPath).forward(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
