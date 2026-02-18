import { Component, Input, Output, EventEmitter, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface WorkflowSettings {
  errorWorkflow?: string;
  saveExecutionProgress?: string;       // 'default' | 'yes' | 'no'
  saveManualExecutions?: string;         // 'default' | 'yes' | 'no'
  executionTimeout?: number;             // seconds, -1 = disabled
  estimatedTimeSavedPerExecution?: number; // minutes
}

@Component({
  selector: 'app-settings-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" (click)="onBackdropClick($event)">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">Workflow Settings</h3>
          <button class="modal-close" (click)="onCancel()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">Error Workflow</label>
            <select class="form-select" [(ngModel)]="form.errorWorkflow">
              <option value="">None</option>
              @for (wf of availableWorkflows; track wf.id) {
                <option [value]="wf.id">{{ wf.name }}</option>
              }
            </select>
            <span class="form-hint">Workflow to run when this workflow fails</span>
          </div>

          <div class="form-group">
            <label class="form-label">Save Execution Progress</label>
            <select class="form-select" [(ngModel)]="form.saveExecutionProgress">
              <option value="default">Default (Inherit)</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
            <span class="form-hint">Save the execution data of each node so you can resume on error</span>
          </div>

          <div class="form-group">
            <label class="form-label">Save Manual Executions</label>
            <select class="form-select" [(ngModel)]="form.saveManualExecutions">
              <option value="default">Default (Inherit)</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
            <span class="form-hint">Whether to save manual test executions</span>
          </div>

          <div class="form-group">
            <label class="form-label">Timeout After (seconds)</label>
            <input class="form-input"
                   type="number"
                   [(ngModel)]="form.executionTimeout"
                   placeholder="No timeout"
                   min="-1">
            <span class="form-hint">Cancel execution after this many seconds. -1 or empty = no timeout</span>
          </div>

          <div class="form-group">
            <label class="form-label">Estimated Time Saved Per Execution (minutes)</label>
            <input class="form-input"
                   type="number"
                   [(ngModel)]="form.estimatedTimeSavedPerExecution"
                   placeholder="0"
                   min="0">
            <span class="form-hint">Estimated minutes saved per run, used for insights</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="onCancel()">Cancel</button>
          <button class="btn-save" (click)="onSave()">Save</button>
        </div>
      </div>
    </div>
  `,
  styleUrl: '../publish-modal/publish-modal.component.scss',
  styles: [`
    .modal-panel { width: 480px; }
    .btn-save {
      padding: 7px 16px;
      background: hsl(247, 49%, 53%);
      border: 1px solid hsl(247, 49%, 53%);
      color: #fff;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-save:hover {
      background: hsl(247, 49%, 46%);
      border-color: hsl(247, 49%, 46%);
    }
    .form-select {
      background: hsl(0, 0%, 9%);
      border: 1px solid hsl(0, 0%, 24%);
      color: hsl(0, 0%, 96%);
      padding: 8px 12px;
      border-radius: 6px;
      font-size: 0.875rem;
      outline: none;
      font-family: inherit;
    }
    .form-select:focus {
      border-color: hsl(247, 49%, 53%);
      box-shadow: 0 0 0 2px hsla(247, 49%, 53%, 0.15);
    }
    .form-hint {
      font-size: 0.75rem;
      color: hsl(0, 0%, 45%);
    }
  `]
})
export class SettingsModalComponent implements OnInit {
  @Input() settings: WorkflowSettings = {};
  @Input() availableWorkflows: { id: string; name: string }[] = [];
  @Output() saved = new EventEmitter<WorkflowSettings>();
  @Output() cancelled = new EventEmitter<void>();

  form: WorkflowSettings = {};

  ngOnInit(): void {
    this.form = {
      errorWorkflow: this.settings.errorWorkflow || '',
      saveExecutionProgress: this.settings.saveExecutionProgress || 'default',
      saveManualExecutions: this.settings.saveManualExecutions || 'default',
      executionTimeout: this.settings.executionTimeout ?? -1,
      estimatedTimeSavedPerExecution: this.settings.estimatedTimeSavedPerExecution ?? 0
    };
  }

  onSave(): void {
    this.saved.emit({ ...this.form });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onCancel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.onCancel();
  }
}
