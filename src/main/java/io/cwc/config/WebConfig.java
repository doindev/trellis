package io.cwc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${spring.web.resources.static-locations:classpath:/static/}")
    private String[] staticLocations;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Hashed assets (JS/CSS chunks) — cache for 1 year
        registry.addResourceHandler("/*.js", "/*.css")
                .addResourceLocations(staticLocations)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

        // Everything else — always revalidate
        registry.addResourceHandler("/**")
                .addResourceLocations(staticLocations)
                .setCacheControl(CacheControl.noCache());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
