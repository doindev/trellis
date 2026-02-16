package io.trellis.controller;

import io.trellis.service.ChatService;
import io.trellis.service.ChatService.ChatHistoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{workflowId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatHistoryEntry sendMessage(
            @PathVariable String workflowId,
            @RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        return chatService.sendMessage(workflowId, content);
    }

    @GetMapping("/{workflowId}/messages")
    public List<ChatHistoryEntry> getHistory(@PathVariable String workflowId) {
        return chatService.getHistory(workflowId);
    }
}
