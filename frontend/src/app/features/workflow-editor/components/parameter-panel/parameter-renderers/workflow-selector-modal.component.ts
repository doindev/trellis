import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProjectService, WorkflowService } from '../../../../../core/services';
import { Project } from '../../../../../core/models/project.model';
import { Workflow } from '../../../../../core/models';

@Component({
    selector: 'app-workflow-selector-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="modal-backdrop" (click)="closed.emit()"></div>
    <div class="modal d-block" tabindex="-1">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Select Workflow</h5>
            <button type="button" class="btn-close" (click)="closed.emit()"></button>
          </div>
          <div class="modal-body">
            <div class="field">
              <label class="field-label">Project</label>
              <select class="form-select param-input"
                      [(ngModel)]="selectedProjectId"
                      (ngModelChange)="onProjectChange($event)">
                <option [ngValue]="null">-- Select a project --</option>
                @for (project of projects; track project.id) {
                  <option [ngValue]="project.id">{{ project.name }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label class="field-label">Workflow</label>
              <select class="form-select param-input"
                      [(ngModel)]="selectedWorkflowId"
                      [disabled]="!selectedProjectId || loading">
                <option [ngValue]="null">
                  {{ loading ? 'Loading...' : '-- Select a workflow --' }}
                </option>
                @for (wf of workflows; track wf.id) {
                  <option [ngValue]="wf.id">{{ wf.name }}</option>
                }
              </select>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="closed.emit()">Cancel</button>
            <button type="button" class="btn btn-primary"
                    [disabled]="!selectedWorkflowId"
                    (click)="onSelect()">Select</button>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .modal-backdrop {
      position: fixed;
      top: 0; left: 0;
      width: 100%; height: 100%;
      background: hsla(0, 0%, 0%, 0.7);
      z-index: 1040;
    }
    .modal { z-index: 1050; }
    .modal-content {
      background: hsl(0, 0%, 13%);
      color: hsl(0, 0%, 96%);
      border: 1px solid hsl(0, 0%, 24%);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4);
    }
    .modal-header { border-bottom-color: hsl(0, 0%, 24%); }
    .modal-footer { border-top-color: hsl(0, 0%, 24%); }
    .btn-close { filter: invert(1) grayscale(100%) brightness(200%); }
    .field { margin-bottom: 14px; }
    .field-label {
      display: block;
      font-size: 0.8125rem;
      font-weight: 500;
      color: hsl(0, 0%, 96%);
      margin-bottom: 4px;
    }
    .param-input {
      background: hsl(0, 0%, 9%);
      border: 1px solid hsl(0, 0%, 24%);
      color: hsl(0, 0%, 96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus {
      background: hsl(0, 0%, 9%);
      border-color: hsl(247, 49%, 53%);
      box-shadow: 0 0 0 2px hsla(247, 49%, 53%, 0.15);
      color: hsl(0, 0%, 96%);
    }
    .param-input option { background: hsl(0, 0%, 13%); color: hsl(0, 0%, 96%); }
    .btn-secondary {
      background: hsl(0, 0%, 20%);
      border-color: hsl(0, 0%, 30%);
      color: hsl(0, 0%, 90%);
    }
    .btn-primary {
      background: hsl(247, 49%, 53%);
      border-color: hsl(247, 49%, 53%);
      color: #fff;
    }
    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  `]
})
export class WorkflowSelectorModalComponent implements OnInit {
  @Input() excludeWorkflowId = '';
  @Output() selected = new EventEmitter<{ workflowId: string; workflowName: string; projectName: string }>();
  @Output() closed = new EventEmitter<void>();

  projects: Project[] = [];
  workflows: Workflow[] = [];
  selectedProjectId: string | null = null;
  selectedWorkflowId: string | null = null;
  loading = false;

  constructor(
    private projectService: ProjectService,
    private workflowService: WorkflowService,
  ) {}

  ngOnInit(): void {
    this.projectService.list().subscribe(projects => this.projects = projects);
  }

  onProjectChange(projectId: string | null): void {
    this.selectedWorkflowId = null;
    this.workflows = [];
    if (!projectId) return;
    this.loading = true;
    this.workflowService.list({ projectId }).subscribe(wfs => {
      this.workflows = wfs.filter(w => w.id !== this.excludeWorkflowId);
      this.loading = false;
    });
  }

  onSelect(): void {
    if (!this.selectedWorkflowId || !this.selectedProjectId) return;
    const wf = this.workflows.find(w => w.id === this.selectedWorkflowId);
    const proj = this.projects.find(p => p.id === this.selectedProjectId);
    if (wf && proj) {
      this.selected.emit({
        workflowId: wf.id!,
        workflowName: wf.name,
        projectName: proj.name,
      });
    }
  }
}
