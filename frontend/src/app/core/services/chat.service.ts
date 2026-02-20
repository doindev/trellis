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

  sendMessage(sessionId: string, content: string): Observable<ChatMessage> {
    return this.api.post<ChatMessage>(`/chat/sessions/${sessionId}/messages`, { content });
  }

  // WebSocket
  connect(sessionId: string): void {
    if (this.subscribedSessionId === sessionId) return;

    this.disconnect();
    this.subscribedSessionId = sessionId;

    this.ws.subscribe(`/topic/chat/${sessionId}`, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.messagesSubject.next({
          id: data.id,
          sessionId,
          role: data.role || 'assistant',
          content: data.content,
          createdAt: data.timestamp || new Date().toISOString()
        });
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
