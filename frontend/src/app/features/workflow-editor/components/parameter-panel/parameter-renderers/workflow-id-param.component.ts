import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';
import { WorkflowService, ProjectService } from '../../../../../core/services';
import { WorkflowSelectorModalComponent } from './workflow-selector-modal.component';

@Component({
  selector: 'app-workflow-id-param',
  standalone: true,
  imports: [CommonModule, FormsModule, WorkflowSelectorModalComponent],
  template: `
    <div class="param-header">
      <label class="param-label">
        {{ param.displayName }}
        @if (param.required) { <span class="required">*</span> }
      </label>
    </div>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <div class="input-wrapper" [class.has-tooltip]="!!projectName">
      <input type="text"
             class="form-control param-input"
             [ngModel]="value"
             (ngModelChange)="onInputChange($event)"
             (blur)="blurred.emit()"
             [disabled]="readOnly"
             placeholder="Workflow ID">
      <button class="browse-btn" (click)="showModal = true" title="Browse workflows" [disabled]="readOnly">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          <circle cx="15" cy="14" r="3"/><path d="m18 17 2 2"/>
        </svg>
      </button>
      @if (projectName) {
        <div class="tooltip-text">Project: {{ projectName }}</div>
      }
    </div>

    @if (showModal) {
      <app-workflow-selector-modal
        [excludeWorkflowId]="currentWorkflowId"
        (selected)="onWorkflowSelected($event)"
        (closed)="showModal = false" />
    }
  `,
  styles: [`
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
      padding-right: 32px;
    }
    .param-input:focus {
      background: hsl(0,0%,9%);
      border-color: hsl(247,49%,53%);
      box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15);
      color: hsl(0,0%,96%);
    }
    .input-wrapper {
      position: relative;
    }
    .browse-btn {
      position: absolute;
      right: 4px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: hsl(247, 49%, 53%);
      cursor: pointer;
      padding: 2px 4px;
      border-radius: 4px;
      opacity: 0.7;
      display: flex;
      align-items: center;
    }
    .browse-btn:hover { opacity: 1; background: hsla(247, 49%, 53%, 0.1); }
    .browse-btn:disabled { opacity: 0.3; cursor: not-allowed; }

    .tooltip-text {
      display: none;
      position: absolute;
      bottom: calc(100% + 6px);
      left: 0;
      background: hsl(0, 0%, 20%);
      color: hsl(0, 0%, 90%);
      font-size: 0.75rem;
      padding: 4px 8px;
      border-radius: 4px;
      white-space: nowrap;
      pointer-events: none;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      z-index: 10;
    }
    .tooltip-text::after {
      content: '';
      position: absolute;
      top: 100%;
      left: 12px;
      border: 5px solid transparent;
      border-top-color: hsl(0, 0%, 20%);
    }
    .has-tooltip:hover .tooltip-text { display: block; }
  `]
})
export class WorkflowIdParamComponent implements OnInit, OnChanges {
  @Input() param!: NodeParameter;
  @Input() value: any = '';
  @Input() readOnly = false;
  @Input() projectId = '';
  @Input() currentWorkflowId = '';
  @Output() valueChange = new EventEmitter<any>();
  @Output() blurred = new EventEmitter<void>();

  showModal = false;
  projectName = '';
  workflowName = '';

  constructor(
    private workflowService: WorkflowService,
    private projectService: ProjectService,
  ) {}

  ngOnInit(): void {
    this.resolveWorkflowInfo();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] && !changes['value'].firstChange) {
      this.resolveWorkflowInfo();
    }
  }

  onInputChange(val: string): void {
    this.value = val;
    this.valueChange.emit(val);
    this.projectName = '';
    this.workflowName = '';
  }

  onWorkflowSelected(event: { workflowId: string; workflowName: string; projectName: string }): void {
    this.value = event.workflowId;
    this.workflowName = event.workflowName;
    this.projectName = event.projectName;
    this.valueChange.emit(event.workflowId);
    this.showModal = false;
  }

  private resolveWorkflowInfo(): void {
    const id = this.value;
    if (!id || typeof id !== 'string') {
      this.projectName = '';
      this.workflowName = '';
      return;
    }
    this.workflowService.get(id).subscribe({
      next: wf => {
        this.workflowName = wf.name;
        if (wf.projectId) {
          this.projectService.get(wf.projectId).subscribe({
            next: proj => this.projectName = proj.name,
            error: () => this.projectName = '',
          });
        }
      },
      error: () => {
        this.projectName = '';
        this.workflowName = '';
      },
    });
  }
}
