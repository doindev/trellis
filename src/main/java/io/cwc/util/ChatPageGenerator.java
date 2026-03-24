package io.cwc.util;

import java.util.Map;

/**
 * Generates self-contained HTML chat pages for the Chat Trigger node's hosted chat mode.
 * The page includes embedded CSS and JavaScript — no external dependencies required.
 *
 * Communication protocol:
 * - POST to webhookUrl with {@code {action: "sendMessage", chatInput: "...", sessionId: "..."}}
 * - POST with {@code {action: "loadPreviousSession", sessionId: "..."}} to load history
 * - Response body is used as the assistant message
 */
public final class ChatPageGenerator {

    private ChatPageGenerator() {}

    /**
     * Generate the hosted chat HTML page.
     *
     * @param webhookUrl       the POST endpoint URL for sending messages
     * @param config           chat configuration from the node parameters
     * @return complete HTML page string
     */
    public static String generateChatPage(String webhookUrl, Map<String, Object> config) {
        String title = escapeHtml(getStr(config, "title", "Hi there!"));
        String subtitle = escapeHtml(getStr(config, "subtitle", "Start a chat. We're here to help you 24/7."));
        String inputPlaceholder = escapeAttr(getStr(config, "inputPlaceholder", "Type your question.."));
        String getStarted = escapeHtml(getStr(config, "getStarted", "New Conversation"));
        boolean showWelcomeScreen = getBool(config, "showWelcomeScreen", false);
        boolean loadPreviousSession = "memory".equals(getStr(config, "loadPreviousSession", "notSupported"));
        String customCss = getStr(config, "customCss", "");
        String initialMessages = getStr(config, "initialMessages", "");

        // Build initial messages JS array
        StringBuilder initMsgs = new StringBuilder("[");
        if (initialMessages != null && !initialMessages.isBlank()) {
            String[] lines = initialMessages.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (initMsgs.length() > 1) initMsgs.append(",");
                    initMsgs.append(escapeJs(line));
                }
            }
        }
        initMsgs.append("]");

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Chat</title>
                    <style>
                        :root {
                            --chat-primary: #6366f1;
                            --chat-primary-hover: #4f46e5;
                            --chat-bg: #f8f9fb;
                            --chat-header-bg: #18181b;
                            --chat-header-color: #f4f4f5;
                            --chat-msg-user-bg: var(--chat-primary);
                            --chat-msg-user-color: #fff;
                            --chat-msg-bot-bg: #fff;
                            --chat-msg-bot-color: #18181b;
                            --chat-input-bg: #fff;
                            --chat-input-border: #e4e4e7;
                            --chat-font: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        }
                        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                        html, body { width: 100%%; height: 100%%; font-family: var(--chat-font); background: var(--chat-bg); }
                        #chat-app { display: flex; flex-direction: column; height: 100%%; }
                        .chat-header {
                            background: var(--chat-header-bg); color: var(--chat-header-color);
                            padding: 1.25rem 1.5rem; flex-shrink: 0;
                        }
                        .chat-header h1 { font-size: 1.5rem; font-weight: 600; margin-bottom: 0.25rem; }
                        .chat-header p { font-size: 0.875rem; opacity: 0.8; line-height: 1.5; }
                        .chat-messages {
                            flex: 1; overflow-y: auto; padding: 1.5rem;
                            display: flex; flex-direction: column; gap: 0.75rem;
                        }
                        .chat-message {
                            max-width: 75%%; padding: 0.75rem 1rem; border-radius: 1rem;
                            font-size: 0.9375rem; line-height: 1.6; word-wrap: break-word;
                            white-space: pre-wrap;
                        }
                        .chat-message.bot {
                            background: var(--chat-msg-bot-bg); color: var(--chat-msg-bot-color);
                            align-self: flex-start; border-bottom-left-radius: 0.25rem;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                        }
                        .chat-message.user {
                            background: var(--chat-msg-user-bg); color: var(--chat-msg-user-color);
                            align-self: flex-end; border-bottom-right-radius: 0.25rem;
                        }
                        .chat-message.bot pre {
                            background: rgba(0,0,0,0.05); padding: 0.5rem 0.75rem;
                            border-radius: 0.375rem; overflow-x: auto;
                            font-size: 0.8125rem; margin: 0.5rem 0;
                        }
                        .chat-message.bot code {
                            background: rgba(0,0,0,0.05); padding: 0.125rem 0.375rem;
                            border-radius: 0.25rem; font-size: 0.8125rem;
                        }
                        .chat-message.bot pre code { background: none; padding: 0; }
                        .chat-typing {
                            align-self: flex-start; padding: 0.75rem 1rem;
                            background: var(--chat-msg-bot-bg); border-radius: 1rem;
                            border-bottom-left-radius: 0.25rem;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                            display: none; gap: 0.3rem; align-items: center;
                        }
                        .chat-typing.active { display: flex; }
                        .chat-typing span {
                            width: 8px; height: 8px; background: #a1a1aa; border-radius: 50%%;
                            animation: typing 1.4s infinite ease-in-out;
                        }
                        .chat-typing span:nth-child(2) { animation-delay: 0.2s; }
                        .chat-typing span:nth-child(3) { animation-delay: 0.4s; }
                        @keyframes typing {
                            0%%, 80%%, 100%% { transform: scale(0.6); opacity: 0.4; }
                            40%% { transform: scale(1); opacity: 1; }
                        }
                        .chat-input-area {
                            flex-shrink: 0; padding: 1rem 1.5rem; background: var(--chat-bg);
                            border-top: 1px solid var(--chat-input-border);
                            display: flex; gap: 0.5rem; align-items: flex-end;
                        }
                        .chat-input-area textarea {
                            flex: 1; resize: none; border: 1px solid var(--chat-input-border);
                            border-radius: 0.75rem; padding: 0.625rem 1rem; font-size: 0.9375rem;
                            font-family: var(--chat-font); line-height: 1.5;
                            background: var(--chat-input-bg); outline: none;
                            max-height: 8rem; min-height: 2.5rem;
                            transition: border-color 0.15s;
                        }
                        .chat-input-area textarea:focus { border-color: var(--chat-primary); }
                        .chat-input-area button {
                            width: 2.5rem; height: 2.5rem; border: none; border-radius: 50%%;
                            background: var(--chat-primary); color: #fff; cursor: pointer;
                            display: flex; align-items: center; justify-content: center;
                            flex-shrink: 0; transition: background 0.15s;
                        }
                        .chat-input-area button:hover { background: var(--chat-primary-hover); }
                        .chat-input-area button:disabled { opacity: 0.5; cursor: not-allowed; }
                        .chat-input-area button svg { width: 18px; height: 18px; }
                        .welcome-screen {
                            flex: 1; display: flex; flex-direction: column;
                            align-items: center; justify-content: center; gap: 1.5rem; padding: 2rem;
                        }
                        .welcome-screen h2 { font-size: 1.75rem; text-align: center; }
                        .welcome-screen p { font-size: 1rem; color: #52525b; text-align: center; max-width: 400px; }
                        .welcome-screen button {
                            padding: 0.75rem 2rem; border: none; border-radius: 0.5rem;
                            background: var(--chat-primary); color: #fff; font-size: 1rem;
                            cursor: pointer; font-weight: 500; transition: background 0.15s;
                        }
                        .welcome-screen button:hover { background: var(--chat-primary-hover); }
                        .powered-by {
                            text-align: center; padding: 0.5rem; font-size: 0.75rem; color: #a1a1aa;
                        }
                        %s
                    </style>
                </head>
                <body>
                    <div id="chat-app">
                        <div class="chat-header">
                            <h1>%s</h1>
                            <p>%s</p>
                        </div>
                        <div id="welcome-screen" class="welcome-screen" style="display:none">
                            <h2>%s</h2>
                            <p>%s</p>
                            <button onclick="startChat()">%s</button>
                        </div>
                        <div id="chat-body" style="display:none;flex:1;display:flex;flex-direction:column">
                            <div class="chat-messages" id="chat-messages">
                                <div class="chat-typing" id="typing-indicator">
                                    <span></span><span></span><span></span>
                                </div>
                            </div>
                            <div class="chat-input-area">
                                <textarea id="chat-input" placeholder="%s" rows="1"
                                    onkeydown="handleKeydown(event)" oninput="autoResize(this)"></textarea>
                                <button id="send-btn" onclick="sendMessage()" title="Send">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                                         stroke-linecap="round" stroke-linejoin="round">
                                        <line x1="22" y1="2" x2="11" y2="13"/>
                                        <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    </div>
                    <script>
                        const WEBHOOK_URL = %s;
                        const INITIAL_MESSAGES = %s;
                        const SHOW_WELCOME = %s;
                        const LOAD_PREVIOUS = %s;
                        let sessionId = localStorage.getItem('cwc-chat-session') || crypto.randomUUID();
                        localStorage.setItem('cwc-chat-session', sessionId);
                        let isWaiting = false;

                        function init() {
                            if (SHOW_WELCOME) {
                                document.getElementById('welcome-screen').style.display = 'flex';
                                document.getElementById('chat-body').style.display = 'none';
                            } else {
                                startChat();
                            }
                        }

                        async function startChat() {
                            document.getElementById('welcome-screen').style.display = 'none';
                            const body = document.getElementById('chat-body');
                            body.style.display = 'flex';
                            body.style.flex = '1';
                            body.style.flexDirection = 'column';

                            if (LOAD_PREVIOUS) {
                                try {
                                    const resp = await fetch(WEBHOOK_URL, {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/json'},
                                        body: JSON.stringify({action: 'loadPreviousSession', sessionId: sessionId})
                                    });
                                    if (resp.ok) {
                                        const result = await resp.json();
                                        const messages = result.data || result || [];
                                        if (Array.isArray(messages)) {
                                            for (const msg of messages) {
                                                const id = msg.id || [];
                                                const content = msg.kwargs?.content || msg.content || '';
                                                if (!content) continue;
                                                const isHuman = (Array.isArray(id) && id.includes('HumanMessage'))
                                                    || msg.type === 'human' || msg.role === 'user';
                                                addMessage(content, isHuman ? 'user' : 'bot');
                                            }
                                        }
                                    }
                                } catch(e) { console.warn('Failed to load previous session:', e); }
                            }

                            if (INITIAL_MESSAGES.length > 0) {
                                const container = document.getElementById('chat-messages');
                                // Only show initial messages if no previous session was loaded
                                if (container.querySelectorAll('.chat-message').length === 0) {
                                    for (const msg of INITIAL_MESSAGES) {
                                        addMessage(msg, 'bot');
                                    }
                                }
                            }

                            document.getElementById('chat-input').focus();
                            scrollToBottom();
                        }

                        function addMessage(text, role) {
                            const container = document.getElementById('chat-messages');
                            const typing = document.getElementById('typing-indicator');
                            const div = document.createElement('div');
                            div.className = 'chat-message ' + role;
                            if (role === 'bot') {
                                div.innerHTML = renderMarkdown(text);
                            } else {
                                div.textContent = text;
                            }
                            container.insertBefore(div, typing);
                            scrollToBottom();
                        }

                        function renderMarkdown(text) {
                            // Basic markdown rendering: code blocks, inline code, bold, italic, links, lists
                            let html = escapeHtml(text);
                            // Code blocks
                            html = html.replace(/```([\\s\\S]*?)```/g, '<pre><code>$1</code></pre>');
                            // Inline code
                            html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
                            // Bold
                            html = html.replace(/\\*\\*(.+?)\\*\\*/g, '<strong>$1</strong>');
                            // Italic
                            html = html.replace(/\\*(.+?)\\*/g, '<em>$1</em>');
                            // Links
                            html = html.replace(/\\[([^\\]]+)\\]\\(([^)]+)\\)/g,
                                '<a href="$2" target="_blank" rel="noopener">$1</a>');
                            return html;
                        }

                        function escapeHtml(text) {
                            const div = document.createElement('div');
                            div.textContent = text;
                            return div.innerHTML;
                        }

                        async function sendMessage() {
                            const input = document.getElementById('chat-input');
                            const text = input.value.trim();
                            if (!text || isWaiting) return;

                            addMessage(text, 'user');
                            input.value = '';
                            autoResize(input);
                            isWaiting = true;
                            document.getElementById('send-btn').disabled = true;
                            document.getElementById('typing-indicator').classList.add('active');
                            scrollToBottom();

                            try {
                                const resp = await fetch(WEBHOOK_URL, {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/json'},
                                    body: JSON.stringify({
                                        action: 'sendMessage',
                                        chatInput: text,
                                        sessionId: sessionId
                                    })
                                });

                                if (resp.ok) {
                                    const data = await resp.json();
                                    // Extract text from response — handle various formats
                                    let reply = '';
                                    if (typeof data === 'string') {
                                        reply = data;
                                    } else if (data.output) {
                                        reply = data.output;
                                    } else if (data.text) {
                                        reply = data.text;
                                    } else if (data.body) {
                                        reply = typeof data.body === 'string' ? data.body : JSON.stringify(data.body);
                                    } else if (Array.isArray(data)) {
                                        reply = data.map(d => d.output || d.text || JSON.stringify(d)).join('\\n');
                                    } else {
                                        reply = JSON.stringify(data, null, 2);
                                    }
                                    addMessage(reply, 'bot');
                                } else {
                                    addMessage('Sorry, something went wrong. Please try again.', 'bot');
                                }
                            } catch (e) {
                                addMessage('Unable to connect. Please check your connection and try again.', 'bot');
                            } finally {
                                isWaiting = false;
                                document.getElementById('send-btn').disabled = false;
                                document.getElementById('typing-indicator').classList.remove('active');
                                input.focus();
                            }
                        }

                        function handleKeydown(event) {
                            if (event.key === 'Enter' && !event.shiftKey) {
                                event.preventDefault();
                                sendMessage();
                            }
                        }

                        function autoResize(el) {
                            el.style.height = 'auto';
                            el.style.height = Math.min(el.scrollHeight, 128) + 'px';
                        }

                        function scrollToBottom() {
                            const container = document.getElementById('chat-messages');
                            requestAnimationFrame(() => { container.scrollTop = container.scrollHeight; });
                        }

                        init();
                    </script>
                </body>
                </html>
                """.formatted(
                customCss,          // custom CSS
                title,              // header h1
                subtitle,           // header p
                title,              // welcome h2
                subtitle,           // welcome p
                getStarted,         // welcome button
                inputPlaceholder,   // textarea placeholder
                escapeJs(webhookUrl), // WEBHOOK_URL
                initMsgs.toString(), // INITIAL_MESSAGES
                showWelcomeScreen,   // SHOW_WELCOME
                loadPreviousSession  // LOAD_PREVIOUS
        );
    }

    private static String getStr(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static boolean getBool(Map<String, Object> config, String key, boolean defaultValue) {
        Object val = config.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultValue;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private static String escapeAttr(String text) {
        return escapeHtml(text);
    }

    private static String escapeJs(String text) {
        if (text == null) return "''";
        return "'" + text.replace("\\", "\\\\")
                         .replace("'", "\\'")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r") + "'";
    }
}
