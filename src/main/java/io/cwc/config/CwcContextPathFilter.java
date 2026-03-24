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

@Configuration
public class CwcContextPathFilter {

    /** Request attribute set when a request has been matched and stripped by this filter. */
    public static final String CWC_REQUEST = "cwc.contextPathMatched";

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

                if (cwcProperties.isRootContext()) {
                    // No-op — default behavior
                    request.setAttribute(CWC_REQUEST, Boolean.TRUE);
                    filterChain.doFilter(request, response);
                    return;
                }

                String prefix = cwcProperties.getContextPath(); // e.g. "/cwc"
                String uri = request.getRequestURI();

                if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                    // Strip the prefix
                    String newUri = uri.substring(prefix.length());
                    if (newUri.isEmpty()) {
                        newUri = "/";
                    }
                    String finalUri = newUri;
                    HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
                        @Override
                        public String getRequestURI() {
                            return finalUri;
                        }

                        @Override
                        public String getServletPath() {
                            return finalUri;
                        }
                    };
                    wrapped.setAttribute(CWC_REQUEST, Boolean.TRUE);
                    filterChain.doFilter(wrapped, response);
                } else {
                    // Not a CWC request — pass through for the host application
                    filterChain.doFilter(request, response);
                }
            }
        });
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
