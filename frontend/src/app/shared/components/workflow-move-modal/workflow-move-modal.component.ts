import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectService } from '../../../core/services';
import { Project } from '../../../core/models';

@Component({
  selector: 'app-workflow-move-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="cancelled.emit()"></div>
      <div class="modal-dialog">
        <div class="modal-header">
          <h3>Move "{{ workflowName }}"</h3>
          <button class="btn-close-modal" (click)="cancelled.emit()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          @if (loading()) {
            <div class="loading-state">Loading projects...</div>
          } @else if (availableProjects().length === 0) {
            <div class="empty-state">No other projects available</div>
          } @else {
            <p class="move-label">Select a project to move this workflow to:</p>
            <div class="project-list">
              @for (project of availableProjects(); track project.id) {
                <button class="project-item" [class.selected]="selectedId() === project.id"
                        (click)="selectedId.set(project.id!)">
                  @if (getIcon(project)) {
                    <span class="project-icon">{{ getIcon(project) }}</span>
                  }
                  <span class="project-name">{{ project.name }}</span>
                </button>
              }
            </div>
          }
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="cancelled.emit()">Cancel</button>
          <button class="btn-move" [disabled]="!selectedId()" (click)="onConfirm()">Move</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1050;
    }
    .modal-dialog {
      position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
      background: var(--bg-surface, #fff); border-radius: 12px; width: 440px; max-height: 80vh;
      display: flex; flex-direction: column; z-index: 1051; box-shadow: 0 8px 32px rgba(0,0,0,0.2);
    }
    .modal-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 16px 20px; border-bottom: 1px solid var(--border-color, #e5e5e5);
    }
    .modal-header h3 { margin: 0; font-size: 16px; font-weight: 600; }
    .btn-close-modal {
      background: none; border: none; cursor: pointer; padding: 4px;
      color: var(--text-secondary, #666); border-radius: 4px;
    }
    .btn-close-modal:hover { background: var(--bg-hover, #f0f0f0); }
    .modal-body { padding: 16px 20px; overflow-y: auto; flex: 1; }
    .move-label { margin: 0 0 12px; font-size: 13px; color: var(--text-secondary, #666); }
    .loading-state, .empty-state { text-align: center; padding: 24px; color: var(--text-secondary, #666); font-size: 13px; }
    .project-list { display: flex; flex-direction: column; gap: 4px; }
    .project-item {
      display: flex; align-items: center; gap: 10px; padding: 10px 12px;
      background: none; border: 1px solid transparent; border-radius: 8px;
      cursor: pointer; text-align: left; font-size: 14px; transition: all 0.15s;
    }
    .project-item:hover { background: var(--bg-hover, #f5f5f5); }
    .project-item.selected {
      border-color: var(--bs-primary, #7c5cfc);
      background: color-mix(in srgb, var(--bs-primary, #7c5cfc) 8%, transparent);
    }
    .project-icon { font-size: 18px; }
    .project-name { font-weight: 500; }
    .modal-footer {
      display: flex; justify-content: flex-end; gap: 8px;
      padding: 12px 20px; border-top: 1px solid var(--border-color, #e5e5e5);
    }
    .btn-cancel {
      padding: 8px 16px; border: 1px solid var(--border-color, #ddd); border-radius: 8px;
      background: none; cursor: pointer; font-size: 13px; font-weight: 500;
    }
    .btn-cancel:hover { background: var(--bg-hover, #f0f0f0); }
    .btn-move {
      padding: 8px 16px; border: none; border-radius: 8px;
      background: var(--bs-primary, #7c5cfc); color: #fff; cursor: pointer;
      font-size: 13px; font-weight: 500;
    }
    .btn-move:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn-move:not(:disabled):hover { filter: brightness(1.1); }
  `]
})
export class WorkflowMoveModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() workflowName = '';
  @Input() currentProjectId = '';
  @Output() confirmed = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  loading = signal(false);
  availableProjects = signal<Project[]>([]);
  selectedId = signal<string | null>(null);

  constructor(private projectService: ProjectService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.selectedId.set(null);
      this.loadProjects();
    }
  }

  loadProjects(): void {
    this.loading.set(true);
    this.projectService.list().subscribe({
      next: (projects) => {
        this.availableProjects.set(projects.filter(p => p.id !== this.currentProjectId));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  getIcon(project: Project): string {
    if (project.icon?.type === 'emoji' && project.icon.value) return project.icon.value;
    return '';
  }

  onConfirm(): void {
    const id = this.selectedId();
    if (id) this.confirmed.emit(id);
  }
}
