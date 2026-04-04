package io.cwc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class},
    excludeName = {
        "org.me.springframework.security.config.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.ai.autoconfigure.mcp.server.McpServerAutoConfiguration",
        "org.springframework.ai.autoconfigure.mcp.server.McpWebMvcServerAutoConfiguration"
    })
@ComponentScan(
    basePackages = "io.cwc",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "io\\.cwc\\.nodes\\.impl\\.ai\\..*"
    )
)
public class CwcApplication {

	public static void main(String[] args) {
		SpringApplication.run(CwcApplication.class, args);
	}

}
