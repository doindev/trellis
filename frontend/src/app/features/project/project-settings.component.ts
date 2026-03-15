import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProjectService, WorkflowService } from '../../core/services';
import { ProjectMcpEndpoint } from '../../core/services/project.service';
import { Project, ProjectMember } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
    selector: 'app-project-settings',
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

  activeTab: 'general' | 'execution' | 'mcp' = 'general';
  saving = false;
  saveError = '';

  // MCP Instance — per-transport state
  httpEnabled = false;
  httpPath = '';
  httpUrl: string | null = null;
  httpSaving = false;

  sseEnabled = false;
  ssePath = '';
  sseUrl: string | null = null;
  sseSaving = false;

  mcpError = '';

  // Execution settings
  execSaveProgress = 'default';
  execSaveManual = 'default';
  execTimeout = -1;
  execErrorWorkflow = '';
  execWorkflows: { id: string; name: string }[] = [];
  execSaving = false;

  constructor(private projectService: ProjectService, private workflowService: WorkflowService) {}

  open(): void {
    this.visible.set(true);
    this.activeTab = 'general';
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
    this.loadExecutionWorkflows();
    this.loadProjectMcp();
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
        this.populateExecSettings(project);
      }
    });
  }

  private populateExecSettings(project: Project): void {
    const s = project.settings || {};
    this.execSaveProgress = s['saveExecutionProgress'] || 'default';
    this.execSaveManual = s['saveManualExecutions'] || 'default';
    this.execTimeout = s['executionTimeout'] ?? -1;
    this.execErrorWorkflow = s['errorWorkflow'] || '';
  }

  private loadExecutionWorkflows(): void {
    this.workflowService.list().subscribe({
      next: wfs => this.execWorkflows = wfs.map(wf => ({ id: wf.id!, name: wf.name })),
      error: () => this.execWorkflows = []
    });
  }

  saveExecutionSettings(): void {
    this.execSaving = true;
    const settings = {
      ...(this.project()?.settings || {}),
      saveExecutionProgress: this.execSaveProgress,
      saveManualExecutions: this.execSaveManual,
      executionTimeout: this.execTimeout,
      errorWorkflow: this.execErrorWorkflow || null
    };
    this.projectService.update(this.projectId, { settings }).subscribe({
      next: (updated) => {
        this.project.set(updated);
        this.execSaving = false;
        this.saved.emit(updated);
      },
      error: () => this.execSaving = false
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

  loadProjectMcp(): void {
    this.mcpError = '';
    this.projectService.getProjectMcp(this.projectId).subscribe({
      next: (endpoints) => {
        const http = endpoints.find(e => e.transport === 'STREAMABLE_HTTP');
        const sse = endpoints.find(e => e.transport === 'SSE');

        this.httpEnabled = !!http?.enabled;
        this.httpPath = http?.path || '';
        this.httpUrl = http?.url || null;

        this.sseEnabled = !!sse?.enabled;
        this.ssePath = sse?.path || '';
        this.sseUrl = sse?.url || null;

        // Auto-suggest paths if not configured
        if (!this.httpEnabled && !this.httpPath) this.httpPath = '';
        if (!this.sseEnabled && !this.ssePath) this.ssePath = 'sse';
      }
    });
  }

  saveProjectMcpTransport(transport: 'STREAMABLE_HTTP' | 'SSE'): void {
    const isHttp = transport === 'STREAMABLE_HTTP';
    if (isHttp) this.httpSaving = true; else this.sseSaving = true;
    this.mcpError = '';

    this.projectService.updateProjectMcp(this.projectId, {
      enabled: isHttp ? this.httpEnabled : this.sseEnabled,
      path: isHttp ? this.httpPath : this.ssePath,
      transport
    }).subscribe({
      next: (result) => {
        if (isHttp) {
          this.httpEnabled = result.enabled;
          this.httpPath = result.path || '';
          this.httpUrl = result.url || null;
          this.httpSaving = false;
        } else {
          this.sseEnabled = result.enabled;
          this.ssePath = result.path || '';
          this.sseUrl = result.url || null;
          this.sseSaving = false;
        }
      },
      error: (err) => {
        if (isHttp) this.httpSaving = false; else this.sseSaving = false;
        this.mcpError = err.error?.message || err.message || 'Failed to save';
      }
    });
  }

  onHttpPathInput(): void {
    this.httpPath = this.sanitizePath(this.httpPath);
  }

  onSsePathInput(): void {
    this.ssePath = this.sanitizePath(this.ssePath);
  }

  private sanitizePath(value: string): string {
    return value
      .replace(/[^a-zA-Z0-9]/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '')
      .toLowerCase();
  }

  copyUrl(url: string | null): void {
    if (url) {
      navigator.clipboard.writeText(url);
    }
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
