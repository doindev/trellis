package io.cwc.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Forwards all non-API, non-static-resource GET requests to index.html
 * so that Angular's client-side router can handle them.
 *
 * When a CWC context path is configured (e.g. /cwc), the index.html is
 * patched with the correct {@code <base href>} and a global
 * {@code window.__CWC_BASE_PATH__} variable.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SpaForwardController {

    private final CwcProperties cwcProperties;

    private String cachedIndexHtml;

    @PostConstruct
    void init() {
        try {
            ClassPathResource resource = new ClassPathResource("static/index.html");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    cachedIndexHtml = patchHtml(html);
                }
            } else {
                // Frontend not built yet — will be available at runtime
                cachedIndexHtml = null;
            }
        } catch (IOException e) {
            log.warn("Could not read index.html at startup: {}", e.getMessage());
            cachedIndexHtml = null;
        }
    }

    private String patchHtml(String html) {
        String uiPath = cwcProperties.getUiContextPath();   // "" or "/cwc"
        String apiPath = cwcProperties.getContextPath();     // "" or "/myapp"
        String baseHref = uiPath + "/";

        // Replace <base href="/"> with the UI context path
        html = html.replace("<base href=\"/\">", "<base href=\"" + baseHref + "\">");

        // Inject global path variables before </head>
        String script = "<script>"
                + "window.__CWC_BASE_PATH__='" + uiPath + "';"
                + "window.__CWC_API_PATH__='" + apiPath + "';"
                + "</script>";
        html = html.replace("</head>", script + "</head>");

        return html;
    }

    @GetMapping(value = {
            "/",
            "/home/**",
            "/insights/**",
            "/workflows",
            "/workflow/**",
            "/executions",
            "/credentials",
            "/variables",
            "/projects",
            "/templates",
            "/settings",
            "/settings/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> forward(HttpServletRequest request) {
        // When UI context path is non-root, only serve for CWC-matched requests.
        // Return 404 so the host app can serve its own resources.
        if (!cwcProperties.isRootUiContext()
                && request.getAttribute(CwcContextPathFilter.CWC_REQUEST) == null) {
            return ResponseEntity.notFound().build();
        }

        if (cachedIndexHtml != null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(cachedIndexHtml);
        }

        // Fallback: try to read at runtime (e.g. if built after startup)
        try {
            ClassPathResource resource = new ClassPathResource("static/index.html");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    cachedIndexHtml = patchHtml(html);
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(cachedIndexHtml);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read index.html", e);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body("<!DOCTYPE html><html><body>CWC frontend not found.</body></html>");
    }
}
