import { Component, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Execution } from '../../../../core/models';
import { ExecutionService } from '../../../../core/services';

@Component({
  selector: 'app-executions-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="executions-sidebar">
      <div class="sidebar-header">
        <h3>Executions</h3>
        <label class="auto-refresh-toggle">
          <input type="checkbox" [(ngModel)]="autoRefresh" (ngModelChange)="onAutoRefreshChange()">
          Auto-refresh
        </label>
      </div>

      <div class="executions-list" *ngIf="executions.length > 0; else emptyState">
        <div
          class="execution-card"
          *ngFor="let exec of executions"
          [class.active]="exec.id === selectedExecutionId"
          (click)="selectExecution(exec)">
          <div class="exec-header">
            <span class="exec-timestamp">{{ formatDate(exec.startedAt) }}</span>
            <span class="exec-id">#{{ exec.id.substring(0, 8) }}</span>
          </div>
          <div class="exec-details">
            <span class="exec-status" [class]="'status-' + exec.status">
              {{ exec.status }}
            </span>
            <span class="exec-duration" *ngIf="exec.finishedAt && exec.startedAt">
              {{ formatDuration(exec.startedAt, exec.finishedAt) }}
            </span>
            <span class="exec-mode">{{ exec.mode }}</span>
          </div>
          <div class="exec-error" *ngIf="exec.errorMessage">
            {{ exec.errorMessage }}
          </div>
          <button
            class="retry-btn"
            *ngIf="exec.status === 'error'"
            (click)="retryExecution(exec.id, $event)">
            Retry
          </button>
        </div>
      </div>

      <ng-template #emptyState>
        <div class="empty-state">
          <p>No executions yet</p>
          <p class="empty-hint">Run this workflow to see execution history here.</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .executions-sidebar {
      width: 320px;
      height: 100%;
      background: hsl(0, 0%, 13%);
      border-right: 1px solid hsl(0, 0%, 20%);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .sidebar-header {
      padding: 16px;
      border-bottom: 1px solid hsl(0, 0%, 20%);
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-shrink: 0;

      h3 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
        color: hsl(0, 0%, 90%);
      }
    }

    .auto-refresh-toggle {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: hsl(0, 0%, 60%);
      cursor: pointer;

      input[type="checkbox"] {
        accent-color: hsl(247, 49%, 53%);
      }
    }

    .executions-list {
      flex: 1;
      overflow-y: auto;
      padding: 8px;

      &::-webkit-scrollbar {
        width: 6px;
      }
      &::-webkit-scrollbar-track {
        background: transparent;
      }
      &::-webkit-scrollbar-thumb {
        background: hsl(0, 0%, 25%);
        border-radius: 3px;
      }
    }

    .execution-card {
      padding: 12px;
      margin-bottom: 4px;
      border-radius: 6px;
      border: 1px solid transparent;
      cursor: pointer;
      transition: background 0.15s, border-color 0.15s;

      &:hover {
        background: hsl(0, 0%, 17%);
      }

      &.active {
        background: hsl(0, 0%, 17%);
        border-color: hsl(247, 49%, 53%);
      }
    }

    .exec-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }

    .exec-timestamp {
      font-size: 13px;
      font-weight: 600;
      color: hsl(0, 0%, 88%);
    }

    .exec-id {
      font-size: 11px;
      color: hsl(0, 0%, 45%);
      font-family: monospace;
    }

    .exec-details {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
    }

    .exec-status {
      padding: 2px 8px;
      border-radius: 4px;
      font-weight: 500;
      text-transform: capitalize;

      &.status-success {
        color: hsl(142, 71%, 68%);
        background: hsla(142, 71%, 45%, 0.15);
      }
      &.status-error {
        color: hsl(0, 84%, 68%);
        background: hsla(0, 84%, 45%, 0.15);
      }
      &.status-running {
        color: hsl(45, 100%, 68%);
        background: hsla(45, 100%, 45%, 0.15);
      }
      &.status-waiting, &.status-new {
        color: hsl(0, 0%, 60%);
        background: hsla(0, 0%, 45%, 0.15);
      }
    }

    .exec-duration {
      color: hsl(0, 0%, 55%);
    }

    .exec-mode {
      color: hsl(0, 0%, 45%);
      font-size: 11px;
    }

    .exec-error {
      margin-top: 6px;
      font-size: 11px;
      color: hsl(0, 84%, 68%);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .retry-btn {
      margin-top: 8px;
      padding: 4px 12px;
      font-size: 11px;
      background: transparent;
      border: 1px solid hsl(0, 0%, 30%);
      border-radius: 4px;
      color: hsl(0, 0%, 70%);
      cursor: pointer;
      transition: background 0.15s, border-color 0.15s;

      &:hover {
        background: hsl(0, 0%, 20%);
        border-color: hsl(0, 0%, 40%);
      }
    }

    .empty-state {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px;
      text-align: center;

      p {
        margin: 0;
        color: hsl(0, 0%, 55%);
        font-size: 14px;
      }

      .empty-hint {
        margin-top: 8px;
        font-size: 12px;
        color: hsl(0, 0%, 40%);
      }
    }
  `]
})
export class ExecutionsSidebarComponent implements OnInit, OnDestroy {
  @Input() workflowId: string | null = null;
  @Input() selectedExecutionId: string | null = null;
  @Output() executionSelected = new EventEmitter<string>();

  executions: Execution[] = [];
  autoRefresh = true;
  private refreshInterval: ReturnType<typeof setInterval> | null = null;

  constructor(private executionService: ExecutionService) {}

  ngOnInit(): void {
    this.loadExecutions(true);
    this.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  private loadExecutions(autoSelect = false): void {
    if (!this.workflowId) return;
    this.executionService.list({ workflowId: this.workflowId }).subscribe({
      next: (execs) => {
        this.executions = execs;
        if (autoSelect && execs.length > 0 && !this.selectedExecutionId) {
          this.selectExecution(execs[0]);
        }
      }
    });
  }

  selectExecution(exec: Execution): void {
    this.executionSelected.emit(exec.id);
  }

  retryExecution(id: string, event: MouseEvent): void {
    event.stopPropagation();
    this.executionService.retry(id).subscribe({
      next: () => this.loadExecutions()
    });
  }

  onAutoRefreshChange(): void {
    if (this.autoRefresh) {
      this.startAutoRefresh();
    } else {
      this.stopAutoRefresh();
    }
  }

  private startAutoRefresh(): void {
    this.stopAutoRefresh();
    if (this.autoRefresh) {
      this.refreshInterval = setInterval(() => this.loadExecutions(), 5000);
    }
  }

  private stopAutoRefresh(): void {
    if (this.refreshInterval !== null) {
      clearInterval(this.refreshInterval);
      this.refreshInterval = null;
    }
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    if (isToday) return time;
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + time;
  }

  formatDuration(start?: string, end?: string): string {
    if (!start || !end) return '';
    const ms = new Date(end).getTime() - new Date(start).getTime();
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const min = Math.floor(ms / 60000);
    const sec = Math.round((ms % 60000) / 1000);
    return `${min}m ${sec}s`;
  }
}
