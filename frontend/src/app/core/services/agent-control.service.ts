import { Injectable, NgZone, OnDestroy } from '@angular/core';
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
  private canvasPush$ = new Subject<any>();
  private pendingWorkflowData: any = null;
  private listening = false;

  controlRequest$ = this.controlRequests$.asObservable();
  isSessionActive$ = this.activeSession$.asObservable();

  /** Emits when a canvas push arrives while the editor is already open */
  canvasPushReceived$ = this.canvasPush$.asObservable();

  constructor(
    private wsService: WebSocketService,
    private http: HttpClient,
    private router: Router,
    private ngZone: NgZone
  ) {}

  private agentControlTopic = '';

  startListening(): void {
    if (this.listening) return;
    this.listening = true;

    this.wsService.connect();
    const sessionId = this.wsService.getBrowserSessionId();
    this.agentControlTopic = `/topic/agent-control/${sessionId}`;
    this.wsService.subscribe(this.agentControlTopic, (msg) => {
      this.ngZone.run(() => {
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
    });
  }

  private executeBrowserAction(data: any): void {
    if (data.action === 'navigate' && data.targetUrl) {
      this.router.navigateByUrl(data.targetUrl);
    } else if (data.action === 'load_workflow' && data.workflowData) {
      this.pendingWorkflowData = data.workflowData;
      this.router.navigate(['/workflow/new'], { queryParams: { agentLoad: 'true' } });
    } else if (data.action === 'push_canvas' && data.workflowData) {
      // Check if user is already on a workflow editor page
      const url = this.router.url;
      const isOnEditor = url.startsWith('/workflow/');
      if (isOnEditor) {
        // Editor is active — emit directly so it can import the data
        this.canvasPush$.next(data.workflowData);
      } else {
        // Not on editor — store as pending and navigate
        this.pendingWorkflowData = data.workflowData;
        this.router.navigate(['/workflow/new'], { queryParams: { agentLoad: 'true' } });
      }
    }
  }

  respondToRequest(requestId: string, approved: boolean): void {
    this.http.post(`${(window as any).__CWC_BASE_PATH__ || ''}/api/agent-control/${requestId}/respond`, { approved }).subscribe();
  }

  approveRequest(request: AgentControlRequest): void {
    this.respondToRequest(request.requestId, true);
    this.activeSession$.next(true);
  }

  denyRequest(request: AgentControlRequest): void {
    this.respondToRequest(request.requestId, false);
  }

  revokeControl(): void {
    this.http.post(`${(window as any).__CWC_BASE_PATH__ || ''}/api/agent-control/revoke`, {}).subscribe();
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
