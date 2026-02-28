import { Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subject } from 'rxjs';
import { WebSocketService } from './websocket.service';

export interface AgentControlRequest {
  requestId: string;
  toolName: string;
  description: string;
  arguments?: Record<string, any>;
}

@Injectable({ providedIn: 'root' })
export class AgentControlService implements OnDestroy {
  private controlRequests$ = new Subject<AgentControlRequest>();
  private activeSession$ = new BehaviorSubject<boolean>(false);
  private pendingWorkflowData: any = null;
  private listening = false;

  controlRequest$ = this.controlRequests$.asObservable();
  isSessionActive$ = this.activeSession$.asObservable();

  constructor(
    private wsService: WebSocketService,
    private http: HttpClient,
    private router: Router
  ) {}

  private agentControlTopic = '';

  startListening(): void {
    if (this.listening) return;
    this.listening = true;

    this.wsService.connect();
    const sessionId = this.wsService.getBrowserSessionId();
    this.agentControlTopic = `/topic/agent-control/${sessionId}`;
    this.wsService.subscribe(this.agentControlTopic, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.event === 'toolConsentRequest') {
        this.controlRequests$.next({
          requestId: data.requestId,
          toolName: data.toolName,
          description: data.description,
          arguments: data.arguments
        });
      } else if (data.event === 'browserAction') {
        this.executeBrowserAction(data);
      }
    });
  }

  private executeBrowserAction(data: any): void {
    if (data.action === 'navigate' && data.targetUrl) {
      this.router.navigateByUrl(data.targetUrl);
    } else if (data.action === 'load_workflow' && data.workflowData) {
      this.pendingWorkflowData = data.workflowData;
      this.router.navigate(['/workflow/new'], { queryParams: { agentLoad: 'true' } });
    }
  }

  respondToRequest(requestId: string, approved: boolean): void {
    this.http.post(`/api/agent-control/${requestId}/respond`, { approved }).subscribe();
  }

  approveRequest(request: AgentControlRequest): void {
    this.respondToRequest(request.requestId, true);
    this.activeSession$.next(true);
  }

  denyRequest(request: AgentControlRequest): void {
    this.respondToRequest(request.requestId, false);
  }

  revokeControl(): void {
    this.http.post('/api/agent-control/revoke', {}).subscribe();
    this.activeSession$.next(false);
  }

  /** Called by workflow editor to consume pending workflow data */
  consumePendingWorkflowData(): any {
    const data = this.pendingWorkflowData;
    this.pendingWorkflowData = null;
    return data;
  }

  hasPendingWorkflowData(): boolean {
    return this.pendingWorkflowData != null;
  }

  ngOnDestroy(): void {
    if (this.agentControlTopic) {
      this.wsService.unsubscribe(this.agentControlTopic);
    }
    this.listening = false;
  }
}
