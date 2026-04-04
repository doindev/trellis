package io.cwc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Conditionally scans the AI node package when LangChain4j is on the classpath
 * AND the feature is not explicitly disabled via property.
 *
 * The main application excludes io.cwc.nodes.impl.ai from its default component scan;
 * this configuration re-enables it when both conditions are met.
 */
@Configuration
@ConditionalOnClass(name = "dev.langchain4j.model.chat.ChatModel")
@ConditionalOnProperty(name = "cwc.features.langchain4j.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan("io.cwc.nodes.impl.ai")
public class AiNodesScanConfig {
}
