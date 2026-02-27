package io.trellis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class},
    excludeName = {
        "org.me.springframework.security.config.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.ai.autoconfigure.mcp.server.McpServerAutoConfiguration",
        "org.springframework.ai.autoconfigure.mcp.server.McpWebMvcServerAutoConfiguration"
    })
public class TrellisApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrellisApplication.class, args);
	}

}
