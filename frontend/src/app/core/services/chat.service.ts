import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { ApiService } from './api.service';
import { WebSocketService } from './websocket.service';
import { ChatSession, ChatMessage } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {
  private messagesSubject = new Subject<ChatMessage>();
  private subscribedSessionId: string | null = null;

  messages$: Observable<ChatMessage> = this.messagesSubject.asObservable();

  constructor(
    private api: ApiService,
    private ws: WebSocketService
  ) {}

  // Session CRUD
  listSessions(): Observable<ChatSession[]> {
    return this.api.get<ChatSession[]>('/chat/sessions');
  }

  createSession(title?: string, agentId?: string): Observable<ChatSession> {
    return this.api.post<ChatSession>('/chat/sessions', { title: title || 'New Chat', agentId });
  }

  getOrCreateSession(title?: string, agentId?: string, workflowId?: string): Observable<ChatSession> {
    return this.api.post<ChatSession>('/chat/sessions/resolve', { title: title || 'New Chat', agentId, workflowId });
  }

  updateSession(id: string, title: string): Observable<ChatSession> {
    return this.api.put<ChatSession>(`/chat/sessions/${id}`, { title });
  }

  deleteSession(id: string): Observable<void> {
    return this.api.delete<void>(`/chat/sessions/${id}`);
  }

  // Messages
  getMessages(sessionId: string): Observable<ChatMessage[]> {
    return this.api.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`);
  }

  sendMessage(sessionId: string, content: string, canvasState?: any): Observable<ChatMessage> {
    const body: any = { content };
    if (canvasState) body.canvasState = canvasState;
    return this.api.post<ChatMessage>(`/chat/sessions/${sessionId}/messages`, body);
  }

  interruptChat(sessionId: string): Observable<void> {
    return this.api.post<void>(`/chat/sessions/${sessionId}/interrupt`, {});
  }

  // WebSocket
  connect(sessionId: string): void {
    if (this.subscribedSessionId === sessionId) return;

    this.disconnect();
    this.subscribedSessionId = sessionId;

    this.ws.subscribe(`/topic/chat/${sessionId}`, (message) => {
      try {
        const data = JSON.parse(message.body);
        if (data.type === 'status') {
          this.messagesSubject.next({
            id: 'status-' + Date.now(),
            sessionId,
            role: 'status',
            content: data.content,
            createdAt: new Date().toISOString()
          });
        } else {
          this.messagesSubject.next({
            id: data.id,
            sessionId,
            role: data.role || 'assistant',
            content: data.content,
            createdAt: data.timestamp || new Date().toISOString()
          });
        }
      } catch (e) {
        console.error('Failed to parse chat message:', e);
      }
    });
  }

  disconnect(): void {
    if (this.subscribedSessionId) {
      this.ws.unsubscribe(`/topic/chat/${this.subscribedSessionId}`);
      this.subscribedSessionId = null;
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
