package io.cwc.config;

import java.io.IOException;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Strips the CWC context path prefix from incoming requests so that
 * CWC controllers can use simple paths like /api/workflows instead of
 * /cwc/api/workflows.
 *
 * <p>Supports separate UI and API context paths:
 * <pre>
 *   cwc.context-path=/        → API at /api/*, webhooks at /webhook/*
 *   cwc.ui.context-path=/cwc  → UI at /cwc/home/*, /cwc/settings/*
 * </pre>
 */
@Configuration
public class CwcContextPathFilter {

    /** Request attribute set when a request has been matched and stripped by this filter. */
    public static final String CWC_REQUEST = "cwc.contextPathMatched";

    /** Set to "ui" or "api" to indicate which context was matched. */
    public static final String CWC_REQUEST_TYPE = "cwc.contextPathType";

    private final CwcProperties cwcProperties;

    public CwcContextPathFilter(CwcProperties cwcProperties) {
        this.cwcProperties = cwcProperties;
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> cwcContextPathFilterRegistration() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String uri = request.getRequestURI();
                String uiPrefix = cwcProperties.getUiContextPath();   // e.g. "/cwc" or ""
                String apiPrefix = cwcProperties.getContextPath();     // e.g. "" or "/myapp"

                // ── UI context path match ──
                if (!uiPrefix.isEmpty() && (uri.equals(uiPrefix) || uri.startsWith(uiPrefix + "/"))) {
                    String newUri = uri.substring(uiPrefix.length());
                    if (newUri.isEmpty()) newUri = "/";
                    filterChain.doFilter(wrapRequest(request, newUri, "ui"), response);
                    return;
                }

                // ── API context path match ──
                if (!apiPrefix.isEmpty() && (uri.equals(apiPrefix) || uri.startsWith(apiPrefix + "/"))) {
                    String newUri = uri.substring(apiPrefix.length());
                    if (newUri.isEmpty()) newUri = "/";
                    filterChain.doFilter(wrapRequest(request, newUri, "api"), response);
                    return;
                }

                // ── Root context (both paths are "/") ──
                if (uiPrefix.isEmpty() && apiPrefix.isEmpty()) {
                    request.setAttribute(CWC_REQUEST, Boolean.TRUE);
                    request.setAttribute(CWC_REQUEST_TYPE, "both");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Not a CWC request — pass through for the host application
                filterChain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static HttpServletRequest wrapRequest(HttpServletRequest original, String newUri, String type) {
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(original) {
            @Override
            public String getRequestURI() { return newUri; }
            @Override
            public String getServletPath() { return newUri; }
        };
        wrapped.setAttribute(CWC_REQUEST, Boolean.TRUE);
        wrapped.setAttribute(CWC_REQUEST_TYPE, type);
        return wrapped;
    }
}
