import { Component, EventEmitter, Input, Output, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AgentControlRequest } from '../../../core/services/agent-control.service';
import { ApprovalScope } from '../../../core/services/auto-approval.service';

@Component({
    selector: 'app-agent-consent-modal',
    imports: [CommonModule],
    template: `
    @if (request) {
      <div class="acm-overlay">
        <div class="acm-dialog" (click)="$event.stopPropagation()">
          <div class="acm-header">
            <div class="acm-icon">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/>
                <path d="M12 8v4m0 4h.01"/>
              </svg>
            </div>
            <h3 class="acm-title">AI Agent Request</h3>
            <button class="acm-close-btn" (click)="onDeny()" [disabled]="executing" title="Deny">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="acm-body">
            <div class="acm-tool-badge">{{ getToolDisplayName(request.toolName) }}</div>
            <p class="acm-message">{{ request.description }}</p>
            @if (hasVisibleArgs(request.arguments)) {
              <div class="acm-args-info">
                @for (entry of getArgEntries(request.arguments); track entry.key) {
                  <div class="acm-arg-row">
                    <span class="acm-arg-key">{{ entry.key }}:</span>
                    <span class="acm-arg-value">{{ formatArgValue(entry.value) }}</span>
                  </div>
                }
              </div>
            }
            @if (!executing) {
              <div class="acm-timer">Auto-denying in {{ countdown }}s</div>
            }
          </div>
          <div class="acm-footer">
            <button class="acm-btn acm-btn-deny" (click)="onDeny()" [disabled]="executing">Deny</button>
            <div class="acm-split-btn">
              <button #approveBtn class="acm-btn acm-btn-approve" (click)="onApprove(null)" [disabled]="executing">
                @if (executing) {
                  <span class="acm-spinner"></span> Running...
                } @else {
                  Approve
                }
              </button>
              <button class="acm-btn acm-btn-chevron" (click)="showDropdown = !showDropdown" [disabled]="executing">
                <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
                  <path d="M4.427 5.427a.75.75 0 0 1 1.06-.074L8 7.634l2.513-2.28a.75.75 0 0 1 1.006 1.115l-3 2.72a.75.75 0 0 1-1.006 0l-3-2.72a.75.75 0 0 1-.086-1.042z"/>
                </svg>
              </button>
              @if (showDropdown) {
                <div class="acm-dropdown">
                  <button class="acm-dropdown-item" (click)="onApprove({type: 'tool', toolName: request.toolName})">
                    Approve all "{{ getToolDisplayName(request.toolName) }}"
                  </button>
                  @if (request.apiSpec?.category === 'read') {
                    <button class="acm-dropdown-item" (click)="onApprove({type: 'read'})">
                      Approve all read operations
                    </button>
                  }
                  @if (request.apiSpec?.category === 'write') {
                    <button class="acm-dropdown-item" (click)="onApprove({type: 'write'})">
                      Approve all write operations
                    </button>
                  }
                  <div class="acm-dropdown-divider"></div>
                  <button class="acm-dropdown-item" (click)="onApprove({type: 'all'})">
                    Always approve (this session)
                  </button>
                </div>
              }
            </div>
          </div>
        </div>
      </div>
    }
  `,
    styles: [`
    .acm-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10000;
      backdrop-filter: blur(2px);
    }
    .acm-dialog {
      background: var(--color-canvas-default, #fff);
      border-radius: 12px;
      width: 460px;
      max-width: 90vw;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      overflow: visible;
    }
    .acm-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px 24px 12px;
    }
    .acm-icon { color: #f59e0b; flex-shrink: 0; }
    .acm-close-btn {
      margin-left: auto;
      background: none;
      border: none;
      color: var(--color-fg-muted, #8b8ba0);
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;
      display: flex;
      align-items: center;
    }
    .acm-close-btn:hover:not(:disabled) { color: var(--color-fg-default, #1a1a2e); background: var(--color-canvas-subtle, #f0f0f5); }
    .acm-close-btn:disabled { opacity: 0.3; cursor: not-allowed; }
    .acm-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--color-fg-default, #1a1a2e);
    }
    .acm-body { padding: 8px 24px 20px; }
    .acm-tool-badge {
      display: inline-block;
      background: #e0e7ff;
      color: #3730a3;
      font-size: 12px;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: 12px;
      margin-bottom: 10px;
      letter-spacing: 0.02em;
    }
    .acm-message {
      margin: 0 0 16px;
      font-size: 14px;
      line-height: 1.5;
      color: var(--color-fg-muted, #5a5a7a);
    }
    .acm-args-info {
      background: var(--color-canvas-subtle, #f6f8fa);
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 13px;
      margin-bottom: 12px;
      max-height: 200px;
      overflow-y: auto;
    }
    .acm-arg-row { display: flex; gap: 6px; padding: 3px 0; align-items: baseline; }
    .acm-arg-key { font-weight: 600; color: var(--color-fg-default, #1a1a2e); flex-shrink: 0; }
    .acm-arg-value { color: var(--color-fg-muted, #5a5a7a); word-break: break-all; white-space: pre-wrap; }
    .acm-timer { font-size: 12px; color: var(--color-fg-muted, #8b8ba0); text-align: center; }

    .acm-footer {
      display: flex;
      gap: 10px;
      padding: 0 24px 20px;
      justify-content: flex-end;
    }
    .acm-btn {
      padding: 8px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      border: none;
      transition: background-color 0.15s;
    }
    .acm-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .acm-btn-deny {
      background: var(--color-canvas-subtle, #f0f0f5);
      color: var(--color-fg-default, #1a1a2e);
    }
    .acm-btn-deny:hover:not(:disabled) { background: #e0e0e8; }

    /* Split button container */
    .acm-split-btn {
      position: relative;
      display: flex;
    }
    .acm-btn-approve {
      background: #3b82f6;
      color: #fff;
      border-radius: 8px 0 0 8px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .acm-btn-approve:hover:not(:disabled) { background: #2563eb; }

    .acm-btn-chevron {
      background: #2563eb;
      color: #fff;
      border-radius: 0 8px 8px 0;
      padding: 8px 8px;
      border-left: 1px solid rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
    }
    .acm-btn-chevron:hover:not(:disabled) { background: #1d4ed8; }

    /* Dropdown menu */
    .acm-dropdown {
      position: absolute;
      top: 100%;
      right: 0;
      margin-top: 4px;
      background: var(--color-canvas-default, #fff);
      border: 1px solid var(--color-border-default, #d0d7de);
      border-radius: 8px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
      min-width: 260px;
      padding: 4px;
      z-index: 10001;
    }
    .acm-dropdown-item {
      display: block;
      width: 100%;
      text-align: left;
      padding: 8px 12px;
      font-size: 13px;
      color: var(--color-fg-default, #1a1a2e);
      background: none;
      border: none;
      border-radius: 6px;
      cursor: pointer;
    }
    .acm-dropdown-item:hover { background: var(--color-canvas-subtle, #f6f8fa); }
    .acm-dropdown-divider {
      height: 1px;
      background: var(--color-border-default, #d0d7de);
      margin: 4px 8px;
    }

    /* Spinner */
    .acm-spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: acm-spin 0.6s linear infinite;
    }
    @keyframes acm-spin { to { transform: rotate(360deg); } }
  `]
})
export class AgentConsentModalComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() request: AgentControlRequest | null = null;
  @Output() approve = new EventEmitter<ApprovalScope | null>();
  @Output() deny = new EventEmitter<void>();

  @ViewChild('approveBtn') approveBtn!: ElementRef<HTMLButtonElement>;

  countdown = 60;
  executing = false;
  showDropdown = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  private static readonly TOOL_DISPLAY_NAMES: Record<string, string> = {
    cwc_list_node_types: 'List Node Types',
    cwc_get_node_type: 'Get Node Type',
    cwc_list_node_categories: 'List Node Categories',
    cwc_list_projects: 'List Projects',
    cwc_get_project: 'Get Project',
    cwc_create_project: 'Create Project',
    cwc_update_project: 'Update Project',
    cwc_list_workflows: 'List Workflows',
    cwc_get_workflow: 'Get Workflow',
    cwc_create_workflow: 'Create Workflow',
    cwc_update_workflow: 'Update Workflow',
    cwc_publish_workflow: 'Publish Workflow',
    cwc_list_executions: 'List Executions',
    cwc_get_execution: 'Get Execution',
    cwc_execute_workflow: 'Execute Workflow',
    cwc_list_agents: 'List Agents',
    cwc_get_agent: 'Get Agent',
    cwc_create_agent: 'Create Agent',
    cwc_update_agent: 'Update Agent',
    cwc_workflow_guide: 'Workflow Guide',
    cwc_list_browser_sessions: 'List Sessions',
    cwc_browser_control: 'Browser Control',
    cwc_push_to_canvas: 'Push to Canvas'
  };

  ngOnInit(): void {
    this.startTimer();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.approveBtn?.nativeElement?.focus(), 0);
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  getToolDisplayName(toolName: string): string {
    return AgentConsentModalComponent.TOOL_DISPLAY_NAMES[toolName] || toolName;
  }

  hasVisibleArgs(args?: Record<string, any>): boolean {
    if (!args) return false;
    return Object.keys(args).length > 0;
  }

  getArgEntries(args?: Record<string, any>): { key: string; value: any }[] {
    if (!args) return [];
    return Object.entries(args).map(([key, value]) => ({ key, value }));
  }

  formatArgValue(value: any): string {
    if (value === null || value === undefined) return '';
    if (typeof value === 'string') return value;
    try {
      const json = JSON.stringify(value, null, 2);
      return json.length > 300 ? json.substring(0, 300) + '...' : json;
    } catch {
      return String(value);
    }
  }

  private startTimer(): void {
    this.countdown = 60;
    this.executing = false;
    this.showDropdown = false;
    this.timer = setInterval(() => {
      this.countdown--;
      if (this.countdown <= 0) {
        this.onDeny();
      }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  onApprove(scope: ApprovalScope | null): void {
    this.stopTimer();
    this.executing = true;
    this.showDropdown = false;
    this.approve.emit(scope);
  }

  onDeny(): void {
    this.stopTimer();
    this.deny.emit();
  }
}
