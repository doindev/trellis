package io.cwc.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import io.cwc.dto.ChatMessageResponse;
import io.cwc.entity.ChatMessageEntity;
import io.cwc.entity.ChatSessionEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.nodes.core.AiSubNodeInterface;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeRegistry;
import io.cwc.repository.ChatMessageRepository;
import io.cwc.repository.ChatSessionRepository;
import io.cwc.repository.WorkflowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ChatService {

    interface ChatAgent {
        String chat(String message);
    }

    private final Optional<ChatModel> chatModel;
    private final Optional<StreamingChatModel> streamingModel;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WorkflowRepository workflowRepository;
    private final AgentConfigService agentConfigService;
    private final CredentialService credentialService;
    private final NodeRegistry nodeRegistry;
    private final AiSettingsService aiSettingsService;
    private final ChatToolProvider chatToolProvider;
    private final Executor workflowExecutor;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful workflow automation assistant for CWC. You have tools to:
            - Discover node types (cwc_list_node_categories, cwc_list_node_types, cwc_get_node_type)
            - Manage workflows (cwc_list_workflows, cwc_get_workflow, cwc_create_workflow, cwc_update_workflow)
            - Manage agents (cwc_list_agents, cwc_get_agent, cwc_create_agent, cwc_update_agent)
            - Push workflow changes to the user's canvas (cwc_push_to_canvas)
            - View executions (cwc_list_executions, cwc_get_execution)
            - Access workflow building guides (cwc_workflow_guide)
            - Publish workflows (cwc_publish_workflow)

            When building workflows or agents, ALWAYS use cwc_workflow_guide first to understand \
            the correct node wiring format. Be concise and practical.""";

    /** Maps settings provider names to their corresponding node type IDs in the registry. */
    private static final Map<String, String> PROVIDER_TO_NODE_TYPE = Map.ofEntries(
            Map.entry("openai", "openAiChatModel"),
            Map.entry("anthropic", "anthropicChatModel"),
            Map.entry("google", "geminiChatModel"),
            Map.entry("ollama", "ollamaChatModel"),
            Map.entry("mistral", "mistralChatModel"),
            Map.entry("azure-openai", "azureOpenAiChatModel"),
            Map.entry("bedrock", "bedrockChatModel"),
            Map.entry("cohere", "cohereChatModel"),
            Map.entry("deepseek", "deepSeekChatModel"),
            Map.entry("groq", "groqChatModel"),
            Map.entry("openrouter", "openRouterChatModel"),
            Map.entry("xai", "xAiGrokChatModel")
    );

    public ChatService(
            Optional<ChatModel> chatModel,
            Optional<StreamingChatModel> streamingModel,
            SimpMessagingTemplate messagingTemplate,
            ChatMessageRepository chatMessageRepository,
            ChatSessionRepository chatSessionRepository,
            WorkflowRepository workflowRepository,
            AgentConfigService agentConfigService,
            CredentialService credentialService,
            NodeRegistry nodeRegistry,
            AiSettingsService aiSettingsService,
            ChatToolProvider chatToolProvider,
            @org.springframework.beans.factory.annotation.Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.workflowRepository = workflowRepository;
        this.agentConfigService = agentConfigService;
        this.credentialService = credentialService;
        this.nodeRegistry = nodeRegistry;
        this.aiSettingsService = aiSettingsService;
        this.chatToolProvider = chatToolProvider;
        this.workflowExecutor = workflowExecutor;
    }

    @Transactional
    public ChatMessageResponse sendMessage(String sessionId, String content) {
        ChatSessionEntity session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Chat session not found: " + sessionId));

        // Save user message
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role("user")
                .content(content)
                .build();
        chatMessageRepository.save(userMsg);

        // Touch session updatedAt
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        // Resolve everything needed for the LLM response while still in transaction
        String systemPrompt = resolveSystemPrompt(session.getAgentId());
        List<ChatMessageEntity> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        ChatModel agentModel = resolveAgentChatModel(session.getAgentId());
        ChatModel settingsModel = agentModel == null ? resolveSettingsChatModel() : null;

        // Capture security context for the async thread (tool handlers need it)
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // Run LLM response asynchronously so the HTTP response returns immediately.
        // History is already loaded (includes the just-saved user message), so no need
        // to wait for transaction commit.
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                processAiResponse(sessionId, systemPrompt, history,
                        agentModel, settingsModel);
            } catch (Exception e) {
                log.error("Async chat processing failed for session {}: {}", sessionId, e.getMessage(), e);
                try {
                    saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
                } catch (Exception sendErr) {
                    log.error("Failed to send error message for session {}: {}", sessionId, sendErr.getMessage());
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, workflowExecutor).exceptionally(t -> {
            log.error("Uncaught error in chat async task for session {}: {}", sessionId, t.getMessage(), t);
            return null;
        });

        return toResponse(userMsg);
    }

    private void processAiResponse(String sessionId, String systemPrompt,
                                    List<ChatMessageEntity> history,
                                    ChatModel agentModel, ChatModel settingsModel) {
        log.info("processAiResponse called for session {}: agentModel={}, settingsModel={}, historySize={}",
                sessionId, agentModel != null, settingsModel != null, history.size());
        if (agentModel != null) {
            respondWithTools(sessionId, systemPrompt, history, agentModel);
        } else if (settingsModel != null) {
            respondWithTools(sessionId, systemPrompt, history, settingsModel);
        } else if (streamingModel.isPresent()) {
            streamResponse(sessionId, systemPrompt, history);
        } else if (chatModel.isPresent()) {
            syncResponse(sessionId, systemPrompt, history);
        } else {
            saveAndSend(sessionId, "AI chat is not configured. Enable it in Settings > Chat with a provider and API key, or create an Agent with a chat model node configured on the canvas.");
        }
    }

    public List<ChatMessageResponse> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String resolveSystemPrompt(String agentId) {
        if (agentId == null) {
            return DEFAULT_SYSTEM_PROMPT;
        }
        try {
            WorkflowEntity agent = workflowRepository.findById(agentId).orElse(null);
            if (agent == null || !"AGENT".equals(agent.getType())) {
                return DEFAULT_SYSTEM_PROMPT;
            }
            // Extract system prompt from the AI Agent node parameters
            if (agent.getNodes() instanceof List<?> nodeList) {
                for (Object item : nodeList) {
                    if (item instanceof Map<?, ?> nodeMap && "aiAgent".equals(nodeMap.get("type"))) {
                        Object params = nodeMap.get("parameters");
                        if (params instanceof Map<?, ?> paramMap) {
                            Object sysMsg = paramMap.get("systemMessage");
                            if (sysMsg != null && !sysMsg.toString().isBlank()) {
                                return sysMsg.toString();
                            }
                        }
                    }
                }
            }
            return DEFAULT_SYSTEM_PROMPT;
        } catch (Exception e) {
            return DEFAULT_SYSTEM_PROMPT;
        }
    }

    @SuppressWarnings("unchecked")
    private ChatModel resolveAgentChatModel(String agentId) {
        if (agentId == null) return null;

        try {
            AgentConfigService.AgentConfig config = agentConfigService.loadAgentConfig(agentId);
            if (config == null || config.modelNodeConfig() == null) return null;

            Map<String, Object> modelConfig = config.modelNodeConfig();
            String nodeType = (String) modelConfig.get("type");
            Map<String, Object> parameters = modelConfig.get("parameters") instanceof Map<?, ?>
                    ? (Map<String, Object>) modelConfig.get("parameters") : Map.of();
            Map<String, Object> credentialsRef = modelConfig.get("credentials") instanceof Map<?, ?>
                    ? (Map<String, Object>) modelConfig.get("credentials") : Map.of();

            if (nodeType == null) return null;

            // Resolve credentials: extract credential ID and decrypt
            Map<String, Object> decryptedCreds = new HashMap<>();
            for (var entry : credentialsRef.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> credRef) {
                    String credId = (String) ((Map<String, Object>) credRef).get("id");
                    if (credId != null) {
                        decryptedCreds = credentialService.getDecryptedData(credId);
                        break;
                    }
                }
            }

            // Get the node instance from the registry
            var registration = nodeRegistry.getNode(nodeType).orElse(null);
            if (registration == null || !(registration.getNodeInstance() instanceof AiSubNodeInterface subNode)) {
                log.warn("Model node type '{}' not found in registry or is not an AI sub-node", nodeType);
                return null;
            }

            // Build a synthetic execution context with the agent's parameters and credentials
            NodeExecutionContext context = NodeExecutionContext.builder()
                    .parameters(parameters)
                    .credentials(decryptedCreds)
                    .executionMode(NodeExecutionContext.ExecutionMode.INTERNAL)
                    .build();

            Object model = subNode.supplyData(context);
            return model instanceof ChatModel cm ? cm : null;
        } catch (Exception e) {
            log.error("Failed to resolve agent chat model for agent {}: {}", agentId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build a ChatModel from the user's AI settings configured in /settings/chat.
     * Maps the provider name to the corresponding chat model node and uses its
     * supplyData() to construct the model instance.
     */
    private ChatModel resolveSettingsChatModel() {
        try {
            if (!aiSettingsService.isEnabled()) return null;

            var entity = aiSettingsService.getEntity();
            if (entity == null) return null;

            // Decrypt the API key
            String apiKey = aiSettingsService.getDecryptedApiKey();
            if (apiKey == null || apiKey.isBlank()) return null;

            String provider = entity.getProvider();
            String nodeType = PROVIDER_TO_NODE_TYPE.get(provider);
            if (nodeType == null) {
                log.warn("No chat model node mapping for provider '{}'", provider);
                return null;
            }

            var registration = nodeRegistry.getNode(nodeType).orElse(null);
            if (registration == null || !(registration.getNodeInstance() instanceof AiSubNodeInterface subNode)) {
                log.warn("Chat model node '{}' not found in registry for provider '{}'", nodeType, provider);
                return null;
            }

            // Build credentials map with decrypted apiKey (and optional baseUrl)
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("apiKey", apiKey);
            if (entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank()) {
                credentials.put("baseUrl", entity.getBaseUrl());
            }

            // Build parameters map with model name
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("model", entity.getModel());

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .parameters(parameters)
                    .credentials(credentials)
                    .executionMode(NodeExecutionContext.ExecutionMode.INTERNAL)
                    .build();

            Object model = subNode.supplyData(context);
            return model instanceof ChatModel cm ? cm : null;
        } catch (Exception e) {
            log.error("Failed to resolve chat model from AI settings: {}", e.getMessage(), e);
            return null;
        }
    }

    private void respondWithTools(String sessionId, String systemPrompt,
                                   List<ChatMessageEntity> history, ChatModel model) {
        log.info("respondWithTools called for session {}: model={}, historySize={}", sessionId, model.getClass().getSimpleName(), history.size());
        try {
            // First try direct model call to verify model + async pipeline work
            var messages = buildMessages(systemPrompt, history);
            log.info("Calling model.chat() for session {}, message count: {}", sessionId, messages.size());
            ChatResponse chatResponse = model.chat(messages);
            String text = chatResponse.aiMessage().text();
            log.info("model.chat() completed for session {}, response length: {}", sessionId, text != null ? text.length() : 0);
            saveAndSend(sessionId, text);
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage(), e);
            saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
        }
    }

    private void syncResponse(String sessionId, String systemPrompt, List<ChatMessageEntity> history) {
        try {
            var messages = buildMessages(systemPrompt, history);
            ChatResponse response = chatModel.get().chat(messages);
            String text = response.aiMessage().text();
            saveAndSend(sessionId, text);
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage());
            saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
        }
    }

    private void streamResponse(String sessionId, String systemPrompt, List<ChatMessageEntity> history) {
        try {
            var messages = buildMessages(systemPrompt, history);
            StringBuilder fullResponse = new StringBuilder();

            streamingModel.get().chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullResponse.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    saveAndSend(sessionId, fullResponse.toString());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming chat error for session {}: {}", sessionId, error.getMessage());
                    saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
                }
            });
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage());
            saveAndSend(sessionId, "Sorry, I encountered an error processing your request.");
        }
    }

    private List<ChatMessage> buildMessages(String systemPrompt, List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        for (ChatMessageEntity entry : history) {
            if ("user".equals(entry.getRole())) {
                messages.add(UserMessage.from(entry.getContent()));
            } else if ("assistant".equals(entry.getRole())) {
                messages.add(AiMessage.from(entry.getContent()));
            }
        }

        return messages;
    }

    private void saveAndSend(String sessionId, String content) {
        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(content)
                .build();
        chatMessageRepository.save(assistantMsg);
        sendToWebSocket(sessionId, assistantMsg);
    }

    private void sendToWebSocket(String sessionId, ChatMessageEntity msg) {
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + sessionId, Map.of(
                    "id", msg.getId(),
                    "role", msg.getRole(),
                    "content", msg.getContent(),
                    "timestamp", msg.getCreatedAt().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to send chat message via WebSocket: {}", e.getMessage());
        }
    }

    private ChatMessageResponse toResponse(ChatMessageEntity entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
