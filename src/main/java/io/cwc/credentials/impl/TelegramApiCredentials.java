package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
