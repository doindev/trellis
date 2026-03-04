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
      <div class="move-overlay" (click)="onOverlayClick($event)">
        <div class="move-dialog">
          <div class="move-header">
            <h3>Move "{{ workflowName }}"</h3>
            <button class="btn-close-modal" (click)="cancelled.emit()">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="move-body">
            @if (loading()) {
              <div class="loading-state">Loading projects...</div>
            } @else if (availableProjects().length === 0) {
              <div class="empty-state">No other projects available</div>
            } @else {
              <p class="move-label">Select a project to move this workflow to:</p>
              <div class="project-list">
                @for (project of availableProjects(); track project.id) {
                  <button type="button" class="project-item"
                          [class.selected]="selectedId() === project.id"
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
          <div class="move-footer">
            <button type="button" class="btn-cancel" (click)="cancelled.emit()">Cancel</button>
            <button type="button" class="btn-move" [disabled]="!selectedId()" (click)="onConfirm()">Move</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .move-overlay {
      position: fixed;
      inset: 0;
      background: hsla(0, 0%, 0%, 0.7);
      z-index: 1050;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .move-dialog {
      background: hsl(0, 0%, 13%);
      color: hsl(0, 0%, 96%);
      border: 1px solid hsl(0, 0%, 24%);
      border-radius: 12px;
      width: 440px;
      max-height: 80vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }
    .move-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 20px;
      border-bottom: 1px solid hsl(0, 0%, 24%);
    }
    .move-header h3 {
      margin: 0;
      font-size: 1rem;
      font-weight: 600;
      color: hsl(0, 0%, 96%);
    }
    .btn-close-modal {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      color: hsl(0, 0%, 50%);
      border-radius: 4px;
    }
    .btn-close-modal:hover {
      background: hsl(0, 0%, 20%);
      color: hsl(0, 0%, 80%);
    }
    .move-body {
      padding: 16px 20px;
      overflow-y: auto;
      flex: 1;
    }
    .move-label {
      margin: 0 0 12px;
      font-size: 0.8125rem;
      color: hsl(0, 0%, 58%);
    }
    .loading-state, .empty-state {
      text-align: center;
      padding: 24px;
      color: hsl(0, 0%, 50%);
      font-size: 0.8125rem;
    }
    .project-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .project-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: none;
      border: 1px solid transparent;
      border-radius: 8px;
      cursor: pointer;
      text-align: left;
      font-size: 0.875rem;
      color: hsl(0, 0%, 88%);
      transition: all 0.15s;
    }
    .project-item:hover {
      background: hsl(0, 0%, 18%);
    }
    .project-item.selected {
      border-color: hsl(7, 100%, 68%);
      background: hsla(7, 100%, 68%, 0.08);
    }
    .project-icon { font-size: 18px; }
    .project-name { font-weight: 500; }
    .move-footer {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 12px 20px;
      border-top: 1px solid hsl(0, 0%, 24%);
    }
    .btn-cancel {
      padding: 8px 16px;
      border: 1px solid hsl(0, 0%, 24%);
      border-radius: 8px;
      background: none;
      color: hsl(0, 0%, 80%);
      cursor: pointer;
      font-size: 0.8125rem;
      font-weight: 500;
    }
    .btn-cancel:hover {
      background: hsl(0, 0%, 20%);
    }
    .btn-move {
      padding: 8px 16px;
      border: none;
      border-radius: 8px;
      background: hsl(7, 100%, 68%);
      color: #fff;
      cursor: pointer;
      font-size: 0.8125rem;
      font-weight: 500;
    }
    .btn-move:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .btn-move:not(:disabled):hover {
      filter: brightness(1.1);
    }
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

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('move-overlay')) {
      this.cancelled.emit();
    }
  }

  onConfirm(): void {
    const id = this.selectedId();
    if (id) this.confirmed.emit(id);
  }
}
