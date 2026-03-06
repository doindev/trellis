package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import io.cwc.service.BrowserSessionRegistry;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserSessionChannelInterceptor implements ChannelInterceptor {

    private final BrowserSessionRegistry registry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String browserSessionId = accessor.getFirstNativeHeader("browserSessionId");
            String wsSessionId = accessor.getSessionId();
            if (browserSessionId != null && !browserSessionId.isBlank() && wsSessionId != null) {
                registry.registerSession(browserSessionId, wsSessionId);
            }
        } else if (StompCommand.DISCONNECT.equals(command)) {
            String wsSessionId = accessor.getSessionId();
            if (wsSessionId != null) {
                registry.unregisterByWebSocketSession(wsSessionId);
            }
        }

        return message;
    }
}
