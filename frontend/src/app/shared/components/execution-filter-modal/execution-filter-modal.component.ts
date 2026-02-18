import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface ExecutionFilters {
  status: string;
  workflowId: string;
  startDateFrom: string;
  startDateTo: string;
  tags: string;
  runTimeOp: 'any' | '>' | '<';
  runTimeMs: number | null;     // run time threshold in milliseconds (null = no filter)
  dataKey: string;
  dataValue: string;
  dataExactMatch: boolean;
}

export function defaultExecutionFilters(): ExecutionFilters {
  return {
    status: 'all',
    workflowId: 'all',
    startDateFrom: '',
    startDateTo: '',
    tags: '',
    runTimeOp: 'any',
    runTimeMs: null,
    dataKey: '',
    dataValue: '',
    dataExactMatch: false,
  };
}

export function isFilterActive(f: ExecutionFilters): boolean {
  return (
    f.status !== 'all' ||
    f.workflowId !== 'all' ||
    f.startDateFrom !== '' ||
    f.startDateTo !== '' ||
    f.tags !== '' ||
    (f.runTimeOp !== 'any' && f.runTimeMs !== null) ||
    f.dataKey !== '' ||
    f.dataValue !== ''
  );
}

@Component({
  selector: 'app-execution-filter-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="filter-modal-backdrop" (click)="closed.emit()"></div>
    <div class="filter-modal">
      <div class="filter-modal-header">
        <h3 class="filter-modal-title">Filter Executions</h3>
        <button class="filter-modal-close" (click)="closed.emit()">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="filter-modal-body">
        <div class="filter-grid">
          @if (showWorkflowFilter) {
            <div class="exec-filter-group">
              <label class="exec-filter-label" for="exec-filter-workflow">Workflows</label>
              <select class="exec-filter-select" id="exec-filter-workflow"
                      [ngModel]="filters.workflowId"
                      (ngModelChange)="updateFilter('workflowId', $event)">
                <option value="all">All workflows</option>
                @for (wf of workflows; track wf.id) {
                  <option [value]="wf.id">{{ wf.name }}</option>
                }
              </select>
            </div>
          }

          <div class="exec-filter-group">
            <label class="exec-filter-label" for="exec-filter-status">Status</label>
            <select class="exec-filter-select" id="exec-filter-status"
                    [ngModel]="filters.status"
                    (ngModelChange)="updateFilter('status', $event)">
              <option value="all">All statuses</option>
              <option value="success">Success</option>
              <option value="error">Error</option>
              <option value="running">Running</option>
              <option value="waiting">Waiting</option>
            </select>
          </div>

          <div class="exec-filter-group exec-filter-group-full">
            <label class="exec-filter-label">Execution start</label>
            <div class="exec-filter-date-range">
              <input type="datetime-local" class="exec-filter-input" placeholder="Earliest"
                     [ngModel]="filters.startDateFrom"
                     (ngModelChange)="updateFilter('startDateFrom', $event)">
              <span class="exec-filter-date-sep">to</span>
              <input type="datetime-local" class="exec-filter-input" placeholder="Latest"
                     [ngModel]="filters.startDateTo"
                     (ngModelChange)="updateFilter('startDateTo', $event)">
            </div>
          </div>

          <div class="exec-filter-group">
            <label class="exec-filter-label" for="exec-filter-tags">Execution tags</label>
            <input type="text" class="exec-filter-input" id="exec-filter-tags" placeholder="Filter by tags"
                   [ngModel]="filters.tags"
                   (ngModelChange)="updateFilter('tags', $event)">
          </div>

          <div class="exec-filter-group">
            <label class="exec-filter-label">Run time (ms)</label>
            <div class="exec-filter-runtime-row">
              <select class="exec-filter-select exec-filter-runtime-op"
                      [ngModel]="filters.runTimeOp"
                      (ngModelChange)="updateFilter('runTimeOp', $event)">
                <option value="any">any</option>
                <option value=">">&gt;</option>
                <option value="<">&lt;</option>
              </select>
              <input type="text" class="exec-filter-input exec-filter-runtime-ms"
                     placeholder="e.g. 5000"
                     [ngModel]="runTimeMsDisplay"
                     (ngModelChange)="onRunTimeMsChange($event)">
            </div>
          </div>

          <div class="exec-filter-group exec-filter-group-full">
            <label class="exec-filter-label">Highlighted data</label>
            <div class="exec-filter-data-row">
              <input type="text" class="exec-filter-input" placeholder="Key (e.g. ID)"
                     [ngModel]="filters.dataKey"
                     (ngModelChange)="updateFilter('dataKey', $event)">
              <label class="exec-filter-exact-match">
                <button class="exec-checkbox" [class.checked]="filters.dataExactMatch"
                        (click)="updateFilter('dataExactMatch', !filters.dataExactMatch)"
                        role="checkbox" [attr.aria-checked]="filters.dataExactMatch">
                  @if (filters.dataExactMatch) {
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 6L9 17l-5-5"/>
                    </svg>
                  }
                </button>
                <span>Exact match</span>
              </label>
              <input type="text" class="exec-filter-input" placeholder="Value (e.g. 123)"
                     [ngModel]="filters.dataValue"
                     (ngModelChange)="updateFilter('dataValue', $event)">
            </div>
          </div>
        </div>
      </div>
      <div class="filter-modal-footer">
        @if (filterActive) {
          <button class="btn-reset-filters" (click)="onReset()">Reset filters</button>
        }
        <button class="btn-apply-filters" (click)="closed.emit()">Apply</button>
      </div>
    </div>
  `,
  styles: [`
    .filter-modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.6);
      z-index: 1000;
    }

    .filter-modal {
      position: fixed;
      top: 80px;
      left: 50%;
      transform: translateX(-50%);
      width: 540px;
      max-width: calc(100vw - 40px);
      max-height: calc(100vh - 120px);
      background: hsl(0, 0%, 13%);
      border: 1px solid hsl(0, 0%, 20%);
      border-radius: 12px;
      z-index: 1001;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .filter-modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 20px;
      border-bottom: 1px solid hsl(0, 0%, 20%);
    }

    .filter-modal-title {
      font-size: 0.9375rem;
      font-weight: 600;
      color: hsl(0, 0%, 90%);
      margin: 0;
    }

    .filter-modal-close {
      background: none;
      border: none;
      color: hsl(0, 0%, 60%);
      padding: 4px;
      border-radius: 6px;
      cursor: pointer;
      display: flex;

      &:hover {
        background: hsl(0, 0%, 17%);
        color: hsl(0, 0%, 90%);
      }
    }

    .filter-modal-body {
      padding: 20px;
      overflow-y: auto;
      flex: 1;
      min-height: 0;
    }

    .filter-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .exec-filter-group {
      margin-bottom: 8px;

      &-full {
        grid-column: 1 / -1;
      }
    }

    .exec-filter-label {
      display: block;
      font-size: 0.75rem;
      font-weight: 600;
      color: hsl(0, 0%, 60%);
      margin-bottom: 6px;
    }

    .exec-filter-select,
    .exec-filter-input {
      width: 100%;
      padding: 8px 12px;
      background: hsl(0, 0%, 9%);
      border: 1px solid hsl(0, 0%, 20%);
      border-radius: 6px;
      color: hsl(0, 0%, 90%);
      font-size: 0.8125rem;
      outline: none;

      &::placeholder {
        color: hsl(0, 0%, 40%);
      }

      &:focus {
        border-color: hsl(247, 49%, 53%);
      }
    }

    .exec-filter-select {
      cursor: pointer;

      option {
        background: hsl(0, 0%, 13%);
      }
    }

    .exec-filter-date-range {
      display: flex;
      align-items: center;
      gap: 10px;

      input {
        flex: 1;
      }
    }

    .exec-filter-date-sep {
      font-size: 0.75rem;
      color: hsl(0, 0%, 60%);
      flex-shrink: 0;
    }

    .exec-filter-runtime-row {
      display: flex;
      gap: 0;
    }

    .exec-filter-runtime-op {
      width: auto;
      flex: 0 0 auto;
      border-radius: 6px 0 0 6px;
      border-right: none;
      padding: 8px 10px;
    }

    .exec-filter-runtime-ms {
      flex: 1;
      border-radius: 0 6px 6px 0;
    }

    .exec-filter-data-row {
      display: flex;
      align-items: center;
      gap: 10px;

      input {
        flex: 1;
      }
    }

    .exec-filter-exact-match {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-shrink: 0;
      cursor: pointer;

      span {
        font-size: 0.75rem;
        color: hsl(0, 0%, 60%);
        white-space: nowrap;
      }
    }

    .exec-checkbox {
      width: 18px;
      height: 18px;
      border: 1px solid hsl(0, 0%, 20%);
      border-radius: 4px;
      background: transparent;
      color: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      padding: 0;
      transition: all 0.15s ease;

      &.checked {
        background: hsl(247, 49%, 53%);
        border-color: hsl(247, 49%, 53%);
        color: #fff;
      }
    }

    .filter-modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      padding: 12px 20px;
      border-top: 1px solid hsl(0, 0%, 20%);
    }

    .btn-reset-filters {
      background: none;
      border: none;
      color: hsl(247, 49%, 53%);
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
      padding: 0;

      &:hover {
        text-decoration: underline;
      }
    }

    .btn-apply-filters {
      padding: 7px 20px;
      background: hsl(247, 49%, 53%);
      color: #fff;
      border: none;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;

      &:hover {
        filter: brightness(0.9);
      }
    }
  `]
})
export class ExecutionFilterModalComponent {
  @Input() filters: ExecutionFilters = defaultExecutionFilters();
  @Input() workflows: { id?: string; name: string }[] = [];
  @Input() showWorkflowFilter = true;
  @Output() filtersChange = new EventEmitter<ExecutionFilters>();
  @Output() closed = new EventEmitter<void>();

  get filterActive(): boolean {
    return isFilterActive(this.filters);
  }

  get runTimeMsDisplay(): string {
    return this.filters.runTimeMs !== null ? String(this.filters.runTimeMs) : '';
  }

  onRunTimeMsChange(value: string): void {
    const digits = value.replace(/[^0-9]/g, '');
    this.updateFilter('runTimeMs', digits === '' ? null : Number(digits));
  }

  updateFilter(key: keyof ExecutionFilters, value: any): void {
    this.filters = { ...this.filters, [key]: value };
    this.filtersChange.emit(this.filters);
  }

  onReset(): void {
    this.filters = defaultExecutionFilters();
    this.filtersChange.emit(this.filters);
  }
}
