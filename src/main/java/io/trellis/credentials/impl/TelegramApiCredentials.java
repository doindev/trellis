package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "telegramApi",
        displayName = "Telegram API",
        description = "Telegram Bot API authentication",
        category = "Communication",
        icon = "telegram"
)
public class TelegramApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("botTokenNotice").displayName("Bot Token Notice")
                        .type(ParameterType.NOTICE)
                        .description("Talk to @BotFather on Telegram to create a bot and get the token.")
                        .build(),
                NodeParameter.builder()
                        .name("botToken").displayName("Bot Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
