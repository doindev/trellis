package io.trellis.controller;

import io.trellis.dto.ChatAgentRequest;
import io.trellis.dto.ChatAgentResponse;
import io.trellis.service.ChatAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/agents")
@RequiredArgsConstructor
public class ChatAgentController {

    private final ChatAgentService chatAgentService;

    @GetMapping
    public List<ChatAgentResponse> list() {
        return chatAgentService.listAgents();
    }

    @GetMapping("/{id}")
    public ChatAgentResponse get(@PathVariable String id) {
        return chatAgentService.getAgent(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatAgentResponse create(@RequestBody ChatAgentRequest request) {
        return chatAgentService.createAgent(request);
    }

    @PutMapping("/{id}")
    public ChatAgentResponse update(@PathVariable String id, @RequestBody ChatAgentRequest request) {
        return chatAgentService.updateAgent(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        chatAgentService.deleteAgent(id);
    }
}
