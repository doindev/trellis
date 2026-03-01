import { Component, OnInit, ViewChild, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { WorkflowService, CredentialService, ProjectService } from '../../core/services';
import { Workflow, Credential, Project } from '../../core/models';
import { WorkflowCardComponent } from '../../shared/components/workflow-card/workflow-card.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { CredentialCreateModalComponent } from '../../shared/components/credential-create-modal/credential-create-modal.component';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, KeyRound, Settings, Workflow as WorkflowIcon, Variable, Layers } from 'lucide-angular';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    WorkflowCardComponent,
    ConfirmDialogComponent,
    CredentialCreateModalComponent,
    LucideAngularModule
  ],
  providers: [{ provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ KeyRound, Settings, Workflow: WorkflowIcon, Variable, Layers }) }],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss'
})
export class ProjectDetailComponent implements OnInit {
  @ViewChild('credModal') credModal!: CredentialCreateModalComponent;

  projectId = '';
  project = signal<Project | null>(null);
  activeTab = signal<'workflows' | 'credentials'>('workflows');

  // Create dropdown
  showCreateDropdown = false;
  private createDropdownTimer: ReturnType<typeof setTimeout> | null = null;

  // Workflow state
  workflows = signal<Workflow[]>([]);
  loadingWorkflows = signal(true);
  searchTerm = signal('');
  sortBy = signal('updatedAt');

  filteredWorkflows = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const sort = this.sortBy();
    let wfs = this.workflows();
    if (term) {
      wfs = wfs.filter(w => w.name.toLowerCase().includes(term));
    }
    wfs = [...wfs].sort((a, b) => {
      switch (sort) {
        case 'name': return a.name.localeCompare(b.name);
        case 'nameDesc': return b.name.localeCompare(a.name);
        case 'createdAt': return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
        default: return new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime();
      }
    });
    return wfs;
  });

  // Pagination
  page = signal(1);
  pageSize = signal(10);
  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredWorkflows().length / this.pageSize())));
  pageNumbers = computed(() => Array.from({ length: this.totalPages() }, (_, i) => i + 1));
  pagedWorkflows = computed(() => {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredWorkflows().slice(start, start + this.pageSize());
  });

  // Credential state
  credentials = signal<Credential[]>([]);
  loadingCredentials = signal(true);
  credSearchTerm = signal('');
  credSortBy = signal('updatedAt');
  filteredCredentials = computed(() => {
    const term = this.credSearchTerm().toLowerCase();
    const sort = this.credSortBy();
    let creds = this.credentials();
    if (term) {
      creds = creds.filter(c => c.name.toLowerCase().includes(term) || c.type.toLowerCase().includes(term));
    }
    creds = [...creds].sort((a, b) => {
      switch (sort) {
        case 'name': return a.name.localeCompare(b.name);
        case 'nameDesc': return b.name.localeCompare(a.name);
        case 'createdAt': return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
        default: return new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime();
      }
    });
    return creds;
  });

  // Delete
  deleteTarget = signal<Workflow | null>(null);
  showDeleteConfirm = signal(false);
  credDeleteTarget = signal<Credential | null>(null);
  showCredDeleteConfirm = signal(false);
  credActionsOpenId = signal<string | null>(null);

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private workflowService: WorkflowService,
    private credentialService: CredentialService,
    private projectService: ProjectService
  ) {}

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId') || '';
    const tab = this.route.snapshot.paramMap.get('tab');
    if (tab === 'credentials') {
      this.activeTab.set('credentials');
    }

    this.loadProject();
    this.loadWorkflows();
    this.loadCredentials();
  }

  loadProject(): void {
    this.projectService.get(this.projectId).subscribe({
      next: (project) => this.project.set(project)
    });
  }

  loadWorkflows(): void {
    this.loadingWorkflows.set(true);
    this.workflowService.list({ projectId: this.projectId }).subscribe({
      next: (data) => {
        this.workflows.set(data);
        this.loadingWorkflows.set(false);
      },
      error: () => this.loadingWorkflows.set(false)
    });
  }

  loadCredentials(): void {
    this.loadingCredentials.set(true);
    this.credentialService.listByProject(this.projectId).subscribe({
      next: (data) => {
        this.credentials.set(data);
        this.loadingCredentials.set(false);
      },
      error: () => this.loadingCredentials.set(false)
    });
  }

  setTab(tab: 'workflows' | 'credentials'): void {
    this.activeTab.set(tab);
    this.router.navigate(['/projects', this.projectId, tab]);
  }

  createWorkflow(): void {
    this.router.navigate(['/workflow/new'], { queryParams: { projectId: this.projectId } });
  }

  openWorkflow(workflow: Workflow): void {
    if (workflow.id) {
      this.router.navigate(['/workflow', workflow.id]);
    }
  }

  duplicateWorkflow(workflow: Workflow): void {
    if (!workflow.id) return;
    this.workflowService.duplicate(workflow.id).subscribe({
      next: () => this.loadWorkflows()
    });
  }

  confirmDelete(workflow: Workflow): void {
    this.deleteTarget.set(workflow);
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    const target = this.deleteTarget();
    if (target?.id) {
      this.workflowService.delete(target.id).subscribe({
        next: () => {
          this.workflows.update(wfs => wfs.filter(w => w.id !== target.id));
          this.showDeleteConfirm.set(false);
          this.deleteTarget.set(null);
        }
      });
    }
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
    this.deleteTarget.set(null);
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
  }

  onSortChange(value: string): void {
    this.sortBy.set(value);
    this.page.set(1);
  }

  goToPage(p: number): void {
    if (p >= 1 && p <= this.totalPages()) {
      this.page.set(p);
    }
  }

  onPageSizeChange(value: string): void {
    this.pageSize.set(Number(value));
    this.page.set(1);
  }

  onCredSearchChange(value: string): void {
    this.credSearchTerm.set(value);
  }

  onCredSortChange(value: string): void {
    this.credSortBy.set(value);
  }

  toggleCredActions(id: string, event: Event): void {
    event.stopPropagation();
    this.credActionsOpenId.set(this.credActionsOpenId() === id ? null : id);
  }

  confirmCredDelete(cred: Credential, event: Event): void {
    event.stopPropagation();
    this.credActionsOpenId.set(null);
    this.credDeleteTarget.set(cred);
    this.showCredDeleteConfirm.set(true);
  }

  onCredDeleteConfirmed(): void {
    const target = this.credDeleteTarget();
    if (target?.id) {
      this.credentialService.delete(target.id).subscribe({
        next: () => {
          this.credentials.update(cs => cs.filter(c => c.id !== target.id));
          this.showCredDeleteConfirm.set(false);
          this.credDeleteTarget.set(null);
        }
      });
    }
  }

  onCredDeleteCancelled(): void {
    this.showCredDeleteConfirm.set(false);
    this.credDeleteTarget.set(null);
  }

  credTimeAgo(cred: Credential): string {
    const date = cred.updatedAt || cred.createdAt;
    if (!date) return '';
    const diff = Date.now() - new Date(date).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    return new Date(date).toLocaleDateString();
  }

  getProjectIcon(): string {
    const p = this.project();
    if (p?.icon?.type === 'emoji' && p.icon.value) return p.icon.value;
    return '';
  }

  // Create dropdown
  scheduleCreateDropdownClose(): void {
    this.cancelCreateDropdownClose();
    this.createDropdownTimer = setTimeout(() => {
      this.showCreateDropdown = false;
    }, 300);
  }

  cancelCreateDropdownClose(): void {
    if (this.createDropdownTimer) {
      clearTimeout(this.createDropdownTimer);
      this.createDropdownTimer = null;
    }
  }

  openCreateCredential(): void {
    this.showCreateDropdown = false;
    this.credModal?.openCreate();
  }

  openEditCredential(cred: Credential): void {
    this.credModal?.openEdit(cred);
  }

  onCredentialSaved(): void {
    this.loadCredentials();
  }

  createVariable(): void {
    this.showCreateDropdown = false;
    this.router.navigate(['/home/variables']);
  }

  createCache(): void {
    this.showCreateDropdown = false;
    this.router.navigate(['/home/caches']);
  }
}
