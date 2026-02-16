package io.trellis.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-static-resource GET requests to index.html
 * so that Angular's client-side router can handle them.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/workflows",
            "/workflow/**",
            "/executions",
            "/credentials",
            "/variables",
            "/projects",
            "/templates",
            "/settings",
            "/settings/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
