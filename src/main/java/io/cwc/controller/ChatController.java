package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.*;
import io.cwc.service.ChatService;
import io.cwc.service.ChatSessionService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;

    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions() {
        return chatSessionService.listSessions();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(@RequestBody ChatSessionRequest request) {
        return chatSessionService.createSession(request);
    }

    @PutMapping("/sessions/{id}")
    public ChatSessionResponse updateSession(@PathVariable String id, @RequestBody ChatSessionRequest request) {
        return chatSessionService.updateSession(id, request);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String id) {
        chatSessionService.deleteSession(id);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable String id) {
        return chatService.getHistory(id);
    }

    @PostMapping("/sessions/{id}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatMessageResponse sendMessage(@PathVariable String id, @RequestBody ChatMessageRequest request) {
        return chatService.sendMessage(id, request.getContent(), request.getCanvasState());
    }

    @PostMapping("/sessions/{id}/interrupt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void interruptChat(@PathVariable String id) {
        chatService.interruptChat(id);
    }
}
