import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../../core/services';
import { Project, ProjectMember } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-project-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ConfirmDialogComponent],
  templateUrl: './project-settings.component.html',
  styleUrl: './project-settings.component.scss'
})
export class ProjectSettingsComponent implements OnInit {
  projectId = '';
  project = signal<Project | null>(null);
  members = signal<ProjectMember[]>([]);

  // Form fields
  name = '';
  description = '';
  iconEmoji = '';

  // Add member form
  newMemberEmail = '';
  newMemberRole = 'PROJECT_EDITOR';

  // Delete
  showDeleteConfirm = signal(false);
  projects = signal<Project[]>([]);
  transferProjectId = '';

  saving = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private projectService: ProjectService
  ) {}

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') || '';
    this.loadProject();
    this.loadMembers();
    this.loadProjects();
  }

  loadProject(): void {
    this.projectService.get(this.projectId).subscribe({
      next: (project) => {
        this.project.set(project);
        this.name = project.name;
        this.description = project.description || '';
        this.iconEmoji = project.icon?.value || '';
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
    const icon = this.iconEmoji ? { type: 'emoji', value: this.iconEmoji } : undefined;
    this.projectService.update(this.projectId, {
      name: this.name,
      description: this.description,
      icon: icon as any
    }).subscribe({
      next: (updated) => {
        this.project.set(updated);
        this.saving = false;
      },
      error: () => this.saving = false
    });
  }

  addMember(): void {
    if (!this.newMemberEmail.trim()) return;
    // The backend expects userId; for now we pass the email as userId
    // In a full implementation, we'd look up the user first
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
      next: () => this.router.navigate(['/home/workflows'])
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
