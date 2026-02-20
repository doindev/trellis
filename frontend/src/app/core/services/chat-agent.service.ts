import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ChatAgent } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class ChatAgentService {
  constructor(private api: ApiService) {}

  list(): Observable<ChatAgent[]> {
    return this.api.get<ChatAgent[]>('/chat/agents');
  }

  get(id: string): Observable<ChatAgent> {
    return this.api.get<ChatAgent>(`/chat/agents/${id}`);
  }

  create(agent: Partial<ChatAgent>): Observable<ChatAgent> {
    return this.api.post<ChatAgent>('/chat/agents', agent);
  }

  update(id: string, agent: Partial<ChatAgent>): Observable<ChatAgent> {
    return this.api.put<ChatAgent>(`/chat/agents/${id}`, agent);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/chat/agents/${id}`);
  }
}
