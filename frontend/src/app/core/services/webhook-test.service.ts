import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class WebhookTestService {
  constructor(private api: ApiService) {}

  startListening(workflowId: string, nodeId: string, method: string, path: string): Observable<{ listening: boolean; testUrl: string; webhookId: string }> {
    return this.api.post('/webhooks/test/listen', { workflowId, nodeId, method, path });
  }

  stopListening(workflowId: string): Observable<any> {
    return this.api.delete(`/webhooks/test/${workflowId}`);
  }

  validatePath(workflowId: string, method: string, path: string): Observable<{ available: boolean; message?: string }> {
    return this.api.post('/webhooks/validate-path', { workflowId, method, path });
  }
}
