import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../../core/services';
import { Project, ProjectMember } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-project-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent],
  templateUrl: './project-settings.component.html',
  styleUrl: './project-settings.component.scss'
})
export class ProjectSettingsComponent {
  @Input() projectId = '';
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<Project>();
  @Output() deleted = new EventEmitter<void>();

  visible = signal(false);
  project = signal<Project | null>(null);
  members = signal<ProjectMember[]>([]);

  // Form fields
  name = '';
  description = '';
  contextPath = '';

  // Add member form
  newMemberEmail = '';
  newMemberRole = 'PROJECT_EDITOR';

  // Delete
  showDeleteConfirm = signal(false);
  projects = signal<Project[]>([]);
  transferProjectId = '';

  saving = false;
  saveError = '';

  constructor(private projectService: ProjectService) {}

  open(): void {
    this.visible.set(true);
    this.saveError = '';
    // Populate form immediately from cached project data so it's
    // never stale between save and the next GET response
    const p = this.project();
    if (p) {
      this.name = p.name;
      this.description = p.description || '';
      this.contextPath = p.contextPath || '';
    }
    this.loadProject();
    this.loadMembers();
    this.loadProjects();
  }

  close(): void {
    this.visible.set(false);
    this.closed.emit();
  }

  loadProject(): void {
    this.projectService.get(this.projectId).subscribe({
      next: (project) => {
        this.project.set(project);
        this.name = project.name;
        this.description = project.description || '';
        this.contextPath = project.contextPath || '';
      }
    });
  }

  loadMembers(): void {
    this.projectService.listMembers(this.projectId).subscribe({
      next: (members) => this.members.set(members)
    });
  }

  loadProjects(): void {
    this.projectService.list().subscribe({
      next: (projects) => this.projects.set(projects.filter(p => p.id !== this.projectId))
    });
  }

  saveGeneral(): void {
    this.saving = true;
    this.saveError = '';
    this.projectService.update(this.projectId, {
      name: this.name,
      description: this.description,
      contextPath: this.contextPath
    }).subscribe({
      next: (updated) => {
        this.project.set(updated);
        this.name = updated.name;
        this.description = updated.description || '';
        this.contextPath = updated.contextPath || '';
        this.saving = false;
        this.saved.emit(updated);
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err.message || 'Failed to save project settings';
      }
    });
  }

  addMember(): void {
    if (!this.newMemberEmail.trim()) return;
    this.projectService.addMember(this.projectId, this.newMemberEmail.trim(), this.newMemberRole).subscribe({
      next: () => {
        this.newMemberEmail = '';
        this.loadMembers();
      }
    });
  }

  updateMemberRole(member: ProjectMember, role: string): void {
    this.projectService.updateMember(this.projectId, member.userId, role).subscribe({
      next: () => this.loadMembers()
    });
  }

  removeMember(member: ProjectMember): void {
    this.projectService.removeMember(this.projectId, member.userId).subscribe({
      next: () => this.loadMembers()
    });
  }

  confirmDeleteProject(): void {
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    this.showDeleteConfirm.set(false);
    this.projectService.delete(this.projectId, this.transferProjectId || undefined).subscribe({
      next: () => {
        this.visible.set(false);
        this.deleted.emit();
      }
    });
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
  }

  getRoleLabel(role: string): string {
    switch (role) {
      case 'PROJECT_PERSONAL_OWNER': return 'Owner';
      case 'PROJECT_ADMIN': return 'Admin';
      case 'PROJECT_EDITOR': return 'Editor';
      case 'PROJECT_VIEWER': return 'Viewer';
      default: return role;
    }
  }

  isReadOnly(): boolean {
    return this.project()?.type === 'PERSONAL';
  }
}
