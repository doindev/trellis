package io.cwc.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class DynamicWebhookRequestMatcher implements RequestMatcher {

    private final String chainName;
    private final WebhookSecurityRegistry registry;

    public DynamicWebhookRequestMatcher(String chainName, WebhookSecurityRegistry registry) {
        this.chainName = chainName;
        this.registry = registry;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/webhook/") && !uri.startsWith("/webhook-test/")) {
            return false;
        }

        String path;
        if (uri.startsWith("/webhook-test/")) {
            path = uri.substring("/webhook-test/".length());
        } else {
            path = uri.substring("/webhook/".length());
        }

        String method = request.getMethod();
        return registry.matches(chainName, method, path);
    }

    @Override
    public String toString() {
        return "DynamicWebhookRequestMatcher{chain='" + chainName + "'}";
    }
}
