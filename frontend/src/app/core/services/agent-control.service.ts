import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subject } from 'rxjs';
import { WebSocketService } from './websocket.service';
import { ToolExecutorService, ApiCallSpec } from './tool-executor.service';
import { AutoApprovalService, ApprovalScope } from './auto-approval.service';

export interface AgentControlRequest {
  requestId: string;
  toolName: string;
  description: string;
  arguments?: Record<string, any>;
  apiSpec?: ApiCallSpec;
}

@Injectable({ providedIn: 'root' })
export class AgentControlService implements OnDestroy {
  private controlRequests$ = new Subject<AgentControlRequest>();
  private activeSession$ = new BehaviorSubject<boolean>(false);
  private canvasPush$ = new Subject<any>();
  private pendingWorkflowData: any = null;
  private listening = false;
  private readonly apiBase = (window as any).__CWC_BASE_PATH__ || '';

  controlRequest$ = this.controlRequests$.asObservable();
  isSessionActive$ = this.activeSession$.asObservable();
  canvasPushReceived$ = this.canvasPush$.asObservable();

  /** Browser-local tool names that don't proxy through REST */
  private static readonly BROWSER_LOCAL_TOOLS = new Set([
    'cwc_browser_control', 'cwc_push_to_canvas'
  ]);

  constructor(
    private wsService: WebSocketService,
    private http: HttpClient,
    private router: Router,
    private ngZone: NgZone,
    private toolExecutor: ToolExecutorService,
    private autoApproval: AutoApprovalService
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
          this.handleConsentRequest(data);
        } else if (data.event === 'browserAction') {
          this.executeBrowserAction(data);
        }
      });
    });
  }

  private handleConsentRequest(data: any): void {
    const request: AgentControlRequest = {
      requestId: data.requestId,
      toolName: data.toolName,
      description: data.description,
      arguments: data.arguments,
      apiSpec: data.apiSpec
    };

    const category = data.apiSpec?.category || 'write';

    // Check auto-approval rules
    if (this.autoApproval.shouldAutoApprove(data.toolName, category)) {
      this.approveRequest(request);
      return;
    }

    // Show consent modal
    this.controlRequests$.next(request);
  }

  /**
   * Approve a request: execute the API call through the browser, then send result back.
   * Optionally add an auto-approval rule for future similar requests.
   */
  approveRequest(request: AgentControlRequest, scope?: ApprovalScope): void {
    if (scope) {
      this.autoApproval.addRule(scope);
    }

    this.activeSession$.next(true);

    if (request.apiSpec && !AgentControlService.BROWSER_LOCAL_TOOLS.has(request.toolName)) {
      // Execute the API call through the user's browser session
      this.toolExecutor.execute(request.apiSpec).subscribe({
        next: (result) => this.respondWithResult(request.requestId, true, result, null),
        error: (err) => this.respondWithResult(request.requestId, true, null, err.message || err.error?.message || 'Request failed')
      });
    } else {
      // Browser-local tools (push_to_canvas, browser_control) — approve and let backend handle
      this.respondWithResult(request.requestId, true, { success: true }, null);
    }
  }

  denyRequest(request: AgentControlRequest): void {
    this.respondWithResult(request.requestId, false, null, 'User denied the request to execute ' + request.toolName + '.');
  }

  revokeControl(): void {
    this.http.post(`${this.apiBase}/api/agent-control/revoke`, {}).subscribe();
    this.activeSession$.next(false);
    this.autoApproval.clearRules();
  }

  private respondWithResult(requestId: string, approved: boolean, result: any, error: string | null): void {
    const body: any = { approved };
    if (result !== undefined && result !== null) body.result = result;
    if (error) body.error = error;
    this.http.post(`${this.apiBase}/api/agent-control/${requestId}/respond`, body).subscribe();
  }

  private executeBrowserAction(data: any): void {
    if (data.action === 'navigate' && data.targetUrl) {
      this.router.navigateByUrl(data.targetUrl);
    } else if (data.action === 'load_workflow' && data.workflowData) {
      this.pendingWorkflowData = data.workflowData;
      this.router.navigate(['/workflow/new'], { queryParams: { agentLoad: 'true' } });
    } else if (data.action === 'push_canvas' && data.workflowData) {
      const url = this.router.url;
      const isOnEditor = url.startsWith('/workflow/');
      if (isOnEditor) {
        this.canvasPush$.next(data.workflowData);
      } else {
        this.pendingWorkflowData = data.workflowData;
        this.router.navigate(['/workflow/new'], { queryParams: { agentLoad: 'true' } });
      }
    }
  }

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
