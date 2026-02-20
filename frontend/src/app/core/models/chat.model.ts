export interface ChatSession {
  id: string;
  title: string;
  agentId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface ChatAgent {
  id: string;
  name: string;
  description?: string;
  systemPrompt?: string;
  icon?: string;
  model?: string;
  createdAt: string;
  updatedAt: string;
}
