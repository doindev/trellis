import { Component, EventEmitter, Input, Output, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AgentControlRequest } from '../../../core/services/agent-control.service';

@Component({
  selector: 'app-agent-consent-modal',
  standalone: true,
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
            <div class="acm-timer">Auto-denying in {{ countdown }}s</div>
          </div>
          <div class="acm-footer">
            <button class="acm-btn acm-btn-deny" (click)="onDeny()">Deny</button>
            <button #allowBtn class="acm-btn acm-btn-allow" (click)="onAllow()">Allow</button>
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
      overflow: hidden;
    }
    .acm-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px 24px 12px;
    }
    .acm-icon {
      color: #f59e0b;
      flex-shrink: 0;
    }
    .acm-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--color-fg-default, #1a1a2e);
    }
    .acm-body {
      padding: 8px 24px 20px;
    }
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
    .acm-arg-row {
      display: flex;
      gap: 6px;
      padding: 3px 0;
      align-items: baseline;
    }
    .acm-arg-key {
      font-weight: 600;
      color: var(--color-fg-default, #1a1a2e);
      flex-shrink: 0;
    }
    .acm-arg-value {
      color: var(--color-fg-muted, #5a5a7a);
      word-break: break-all;
      white-space: pre-wrap;
    }
    .acm-timer {
      font-size: 12px;
      color: var(--color-fg-muted, #8b8ba0);
      text-align: center;
    }
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
    .acm-btn-deny {
      background: var(--color-canvas-subtle, #f0f0f5);
      color: var(--color-fg-default, #1a1a2e);
    }
    .acm-btn-deny:hover {
      background: #e0e0e8;
    }
    .acm-btn-allow {
      background: #3b82f6;
      color: #fff;
    }
    .acm-btn-allow:hover {
      background: #2563eb;
    }
  `]
})
export class AgentConsentModalComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() request: AgentControlRequest | null = null;
  @Output() allow = new EventEmitter<void>();
  @Output() deny = new EventEmitter<void>();

  @ViewChild('allowBtn') allowBtn!: ElementRef<HTMLButtonElement>;

  countdown = 60;
  private timer: ReturnType<typeof setInterval> | null = null;

  private static readonly TOOL_DISPLAY_NAMES: Record<string, string> = {
    cwc_list_node_types: 'List Node Types',
    cwc_get_node_type: 'Get Node Type',
    cwc_list_workflows: 'List Workflows',
    cwc_get_workflow: 'Get Workflow',
    cwc_create_workflow: 'Create Workflow',
    cwc_update_workflow: 'Update Workflow',
    cwc_list_executions: 'List Executions',
    cwc_get_execution: 'Get Execution',
    cwc_workflow_guide: 'Workflow Guide',
    cwc_list_browser_sessions: 'List Sessions',
    cwc_browser_control: 'Browser Control',
    cwc_push_to_canvas: 'Push to Canvas',
    cwc_publish_workflow: 'Publish Workflow'
  };

  ngOnInit(): void {
    this.startTimer();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.allowBtn?.nativeElement?.focus(), 0);
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

  onAllow(): void {
    this.stopTimer();
    this.allow.emit();
  }

  onDeny(): void {
    this.stopTimer();
    this.deny.emit();
  }
}
