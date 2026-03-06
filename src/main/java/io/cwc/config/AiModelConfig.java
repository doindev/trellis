package io.cwc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiModelConfig {

    @Bean
    @ConditionalOnProperty("cwc.ai.openai.api-key")
    public ChatModel chatModel(
            @Value("${cwc.ai.openai.api-key}") String apiKey,
            @Value("${cwc.ai.openai.model:gpt-4o-mini}") String model) {
        log.info("Configuring OpenAI ChatModel with model: {}", model);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }

    @Bean
    @ConditionalOnProperty("cwc.ai.openai.api-key")
    public StreamingChatModel streamingChatModel(
            @Value("${cwc.ai.openai.api-key}") String apiKey,
            @Value("${cwc.ai.openai.model:gpt-4o-mini}") String model) {
        log.info("Configuring OpenAI StreamingChatModel with model: {}", model);
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
