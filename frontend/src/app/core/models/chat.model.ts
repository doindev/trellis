export interface ChatSession {
  id: string;
  title: string;
  agentId?: string;
  workflowId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant' | 'status';
  content: string;
  createdAt: string;
}
