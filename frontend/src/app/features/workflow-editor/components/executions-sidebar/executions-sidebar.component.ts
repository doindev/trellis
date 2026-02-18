import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, OnChanges, SimpleChanges, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Execution } from '../../../../core/models';
import { ExecutionService } from '../../../../core/services';
import {
  ExecutionFilters,
  defaultExecutionFilters,
  isFilterActive
} from '../../../../shared/components/execution-filter-modal/execution-filter-modal.component';

@Component({
  selector: 'app-executions-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="executions-sidebar">
      <div class="sidebar-header">
        <h3>Executions</h3>
        <div class="sidebar-header-controls">
          <label class="auto-refresh-toggle">
            <input type="checkbox" [(ngModel)]="autoRefresh" (ngModelChange)="onAutoRefreshChange()">
            Auto-refresh
          </label>
          <button class="btn-filter" [class.active]="filterActive" (click)="filterButtonClicked.emit()" title="Filter executions">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10 20a1 1 0 0 0 .553.895l2 1A1 1 0 0 0 14 21v-7a2 2 0 0 1 .517-1.341L21.74 4.67A1 1 0 0 0 21 3H3a1 1 0 0 0-.742 1.67l7.225 7.989A2 2 0 0 1 10 14z"/>
            </svg>
          </button>
        </div>
      </div>

      <div class="executions-list" *ngIf="displayedExecutions.length > 0; else emptyState">
        <div
          class="execution-card"
          *ngFor="let exec of displayedExecutions"
          [attr.data-execution-id]="exec.id"
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
          @if (filterActive) {
            <p>No executions match filters</p>
            <button class="reset-filters-link" (click)="resetFilters()">Reset filters</button>
          } @else {
            <p>No executions yet</p>
            <p class="empty-hint">Run this workflow to see execution history here.</p>
          }
        </div>
      </ng-template>
    </div>

  `,
  styles: [`
    .executions-sidebar {
      width: 280px;
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
      flex-shrink: 0;

      h3 {
        margin: 0 0 10px 0;
        font-size: 14px;
        font-weight: 600;
        color: hsl(0, 0%, 90%);
      }
    }

    .sidebar-header-controls {
      display: flex;
      align-items: center;
      justify-content: space-between;
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

    .btn-filter {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      background: none;
      border: 1px solid hsl(0, 0%, 20%);
      border-radius: 6px;
      color: hsl(0, 0%, 60%);
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background: hsl(0, 0%, 17%);
        color: hsl(0, 0%, 90%);
      }

      &.active {
        background: hsl(247, 49%, 53%);
        border-color: hsl(247, 49%, 53%);
        color: #fff;
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

    .reset-filters-link {
      margin-top: 12px;
      background: none;
      border: none;
      color: hsl(247, 49%, 53%);
      font-size: 13px;
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }
  `]
})
export class ExecutionsSidebarComponent implements OnInit, OnDestroy, OnChanges {
  @Input() workflowId: string | null = null;
  @Input() selectedExecutionId: string | null = null;
  @Input() filters: ExecutionFilters = defaultExecutionFilters();
  @Output() executionSelected = new EventEmitter<string>();
  @Output() filterButtonClicked = new EventEmitter<void>();
  @Output() filtersChanged = new EventEmitter<ExecutionFilters>();

  executions: Execution[] = [];
  autoRefresh = true;
  private refreshInterval: ReturnType<typeof setInterval> | null = null;

  get filterActive(): boolean {
    return isFilterActive(this.filters);
  }

  get displayedExecutions(): Execution[] {
    let result = this.executions;
    const f = this.filters;

    if (f.status !== 'all') {
      result = result.filter(e => e.status === f.status);
    }
    if (f.startDateFrom) {
      const from = new Date(f.startDateFrom).getTime();
      result = result.filter(e => e.startedAt && new Date(e.startedAt).getTime() >= from);
    }
    if (f.startDateTo) {
      const to = new Date(f.startDateTo).getTime();
      result = result.filter(e => e.startedAt && new Date(e.startedAt).getTime() <= to);
    }
    if (f.runTimeOp !== 'any' && f.runTimeMs !== null) {
      const threshold = f.runTimeMs;
      const op = f.runTimeOp;
      result = result.filter(e => {
        if (!e.startedAt || !e.finishedAt) return false;
        const duration = new Date(e.finishedAt).getTime() - new Date(e.startedAt).getTime();
        return op === '>' ? duration > threshold : duration < threshold;
      });
    }

    return result;
  }

  constructor(private executionService: ExecutionService, private el: ElementRef) {}

  ngOnInit(): void {
    this.loadExecutions(true);
    this.startAutoRefresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedExecutionId'] && this.selectedExecutionId) {
      this.pendingScrollToExecution = true;
      // Try immediately in case cards are already rendered
      setTimeout(() => this.scrollToSelectedExecution());
    }
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  private pendingScrollToExecution = false;

  private scrollToSelectedExecution(): void {
    if (!this.selectedExecutionId) return;
    const card = this.el.nativeElement.querySelector(
      `[data-execution-id="${this.selectedExecutionId}"]`
    );
    if (card) {
      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      this.pendingScrollToExecution = false;
    }
  }

  private loadExecutions(autoSelect = false): void {
    if (!this.workflowId) return;
    this.executionService.list({ workflowId: this.workflowId }).subscribe({
      next: (execs) => {
        this.executions = execs;
        if (autoSelect && execs.length > 0 && !this.selectedExecutionId) {
          this.selectExecution(execs[0]);
        }
        // Scroll after DOM updates with the new list
        if (this.pendingScrollToExecution && this.selectedExecutionId) {
          setTimeout(() => this.scrollToSelectedExecution());
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

  resetFilters(): void {
    this.filtersChanged.emit(defaultExecutionFilters());
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
