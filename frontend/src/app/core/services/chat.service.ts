import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { ApiService } from './api.service';
import { WebSocketService } from './websocket.service';

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {
  private messagesSubject = new Subject<ChatMessage>();
  private subscribedWorkflowId: string | null = null;

  messages$: Observable<ChatMessage> = this.messagesSubject.asObservable();

  constructor(
    private api: ApiService,
    private ws: WebSocketService
  ) {}

  connect(workflowId: string): void {
    if (this.subscribedWorkflowId === workflowId) return;

    this.disconnect();
    this.subscribedWorkflowId = workflowId;

    this.ws.subscribe(`/topic/chat/${workflowId}`, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.messagesSubject.next({
          role: data.role || 'assistant',
          content: data.content,
          timestamp: new Date(data.timestamp || Date.now())
        });
      } catch (e) {
        console.error('Failed to parse chat message:', e);
      }
    });
  }

  disconnect(): void {
    if (this.subscribedWorkflowId) {
      this.ws.unsubscribe(`/topic/chat/${this.subscribedWorkflowId}`);
      this.subscribedWorkflowId = null;
    }
  }

  sendMessage(workflowId: string, content: string): Observable<any> {
    return this.api.post(`/chat/${workflowId}/messages`, { content });
  }

  getHistory(workflowId: string): Observable<ChatMessage[]> {
    return this.api.get<ChatMessage[]>(`/chat/${workflowId}/messages`);
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
