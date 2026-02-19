import { Component, OnInit, ViewChild, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { WorkflowService, ExecutionService, CredentialService, ProjectService } from '../../core/services';
import { Workflow, Execution, Credential, Project } from '../../core/models';
import { WorkflowCardComponent } from '../../shared/components/workflow-card/workflow-card.component';
import { VariableListComponent } from '../variables/variable-list.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { CredentialCreateModalComponent } from '../../shared/components/credential-create-modal/credential-create-modal.component';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, KeyRound, Folder, Table } from 'lucide-angular';
import {
  ExecutionFilterModalComponent,
  ExecutionFilters,
  defaultExecutionFilters,
  isFilterActive
} from '../../shared/components/execution-filter-modal/execution-filter-modal.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    RouterLinkActive,
    WorkflowCardComponent,
    VariableListComponent,
    ConfirmDialogComponent,
    CredentialCreateModalComponent,
    LucideAngularModule,
    ExecutionFilterModalComponent
  ],
  providers: [{ provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ KeyRound, Folder, Table }) }],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  @ViewChild('credModal') credModal!: CredentialCreateModalComponent;

  activeTab = signal<'workflows' | 'credentials' | 'executions' | 'variables' | 'datatables'>('workflows');

  // Workflow state
  workflows = signal<Workflow[]>([]);
  loadingWorkflows = signal(true);
  searchTerm = signal('');
  sortBy = signal('updatedAt');

  // Insights
  executions = signal<Execution[]>([]);
  totalExecutions = computed(() => {
    const execs = this.executions();
    return Array.isArray(execs) ? execs.filter(e => e.mode?.toUpperCase() !== 'MANUAL').length : 0;
  });
  failedExecutions = computed(() => {
    const execs = this.executions();
    return Array.isArray(execs) ? execs.filter(e => e.status?.toUpperCase() === 'ERROR' && e.mode?.toUpperCase() !== 'MANUAL').length : 0;
  });
  failureRate = computed(() => {
    const total = this.totalExecutions();
    if (total === 0) return 0;
    return Math.round((this.failedExecutions() / total) * 100);
  });
  avgRunTimeMs = computed(() => {
    const execs = this.executions();
    if (!Array.isArray(execs)) return 0;
    const finished = execs.filter(e => e.startedAt && e.finishedAt);
    if (finished.length === 0) return 0;
    const totalMs = finished.reduce((sum, e) => {
      return sum + (new Date(e.finishedAt!).getTime() - new Date(e.startedAt!).getTime());
    }, 0);
    return Math.round(totalMs / finished.length);
  });
  // Workflow filters
  showWfFilterModal = signal(false);
  wfTagsFilter = signal('');
  wfStatusFilter = signal('all');
  wfShowArchived = signal(false);
  wfFilterActive = computed(() =>
    this.wfTagsFilter() !== '' ||
    this.wfStatusFilter() !== 'all' ||
    this.wfShowArchived()
  );

  // Filtered + sorted workflows
  filteredWorkflows = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const sort = this.sortBy();
    const statusFilter = this.wfStatusFilter();
    let wfs = this.workflows();

    if (term) {
      wfs = wfs.filter(w => w.name.toLowerCase().includes(term));
    }

    if (statusFilter === 'published') {
      wfs = wfs.filter(w => w.published);
    } else if (statusFilter === 'unpublished') {
      wfs = wfs.filter(w => !w.published);
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

  // Credential actions dropdown
  credActionsOpenId = signal<string | null>(null);

  // Execution tab state
  execAutoRefresh = signal(true);
  execFilters = signal<ExecutionFilters>(defaultExecutionFilters());
  showExecFilterModal = signal(false);
  loadingExecutions = signal(true);
  filteredExecutions = computed(() => {
    const execs = this.executions();
    if (!Array.isArray(execs)) return [];
    const f = this.execFilters();
    let result = execs;
    if (f.status !== 'all') {
      result = result.filter(e => e.status === f.status);
    }
    if (f.workflowId !== 'all') {
      result = result.filter(e => e.workflowId === f.workflowId);
    }
    if (f.startDateFrom) {
      const from = new Date(f.startDateFrom).getTime();
      result = result.filter(e => e.startedAt && new Date(e.startedAt).getTime() >= from);
    }
    if (f.startDateTo) {
      const to = new Date(f.startDateTo).getTime();
      result = result.filter(e => e.startedAt && new Date(e.startedAt).getTime() <= to);
    }
    if (f.runTimeOp !== 'any' && f.runTimeMs !== null) {
      const threshold = f.runTimeMs;
      const op = f.runTimeOp;
      result = result.filter(e => {
        if (!e.startedAt || !e.finishedAt) return false;
        const duration = new Date(e.finishedAt).getTime() - new Date(e.startedAt).getTime();
        return op === '>' ? duration > threshold : duration < threshold;
      });
    }
    return result;
  });
  execFilterActive = computed(() => isFilterActive(this.execFilters()));
  private execRefreshInterval: any = null;

  // Projects
  projectMap = new Map<string, string>();

  // Create dropdown
  showCreateDropdown = false;
  private createDropdownTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private workflowService: WorkflowService,
    private executionService: ExecutionService,
    private credentialService: CredentialService,
    private projectService: ProjectService
  ) {}

  ngOnInit(): void {
    // Set active tab from route param
    const tabFromRoute = this.route.snapshot.paramMap.get('tab');
    if (tabFromRoute && ['workflows', 'credentials', 'executions', 'variables', 'datatables'].includes(tabFromRoute)) {
      this.activeTab.set(tabFromRoute as any);
    }

    // Auto-open credential create modal from sidebar navigation
    const action = this.route.snapshot.queryParamMap.get('action');
    if (action === 'create-credential') {
      this.activeTab.set('credentials');
      // Defer to next tick so ViewChild is available
      setTimeout(() => this.credModal?.openCreate(), 0);
    }

    this.loadWorkflows();
    this.loadExecutions();
    this.loadCredentials();
    this.loadProjects();
  }

  setTab(tab: 'workflows' | 'credentials' | 'executions' | 'variables' | 'datatables'): void {
    this.activeTab.set(tab);
    this.router.navigate(['/home', tab]);
  }

  loadWorkflows(): void {
    this.loadingWorkflows.set(true);
    this.workflowService.list().subscribe({
      next: (data) => {
        this.workflows.set(data);
        this.loadingWorkflows.set(false);
      },
      error: () => this.loadingWorkflows.set(false)
    });
  }

  loadExecutions(): void {
    this.loadingExecutions.set(true);
    this.executionService.list().subscribe({
      next: (data) => {
        this.executions.set(Array.isArray(data) ? data : []);
        this.loadingExecutions.set(false);
      },
      error: () => this.loadingExecutions.set(false)
    });
  }

  loadProjects(): void {
    this.projectService.list().subscribe({
      next: (projects) => {
        this.projectMap.clear();
        projects.forEach(p => {
          if (p.id) this.projectMap.set(p.id, p.name);
        });
      }
    });
  }

  getProjectName(projectId?: string): string {
    if (!projectId) return '';
    return this.projectMap.get(projectId) || '';
  }

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

  createWorkflow(): void {
    this.showCreateDropdown = false;
    this.router.navigate(['/workflow/new']);
  }

  openWorkflow(workflow: Workflow): void {
    if (workflow.id) {
      this.router.navigate(['/workflow', workflow.id]);
    }
  }

  openExecution(exec: Execution): void {
    this.router.navigate(['/workflow', exec.workflowId], {
      queryParams: { tab: 'executions', executionId: exec.id }
    });
  }

  duplicateWorkflow(workflow: Workflow): void {
    if (!workflow.id) return;
    const copy: Partial<Workflow> = {
      name: workflow.name + ' (copy)',
      published: false,
      currentVersion: 0,
      nodes: workflow.nodes,
      connections: workflow.connections,
      settings: workflow.settings
    };
    this.workflowService.create(copy as Workflow).subscribe({
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

  resetWfFilters(): void {
    this.wfTagsFilter.set('');
    this.wfStatusFilter.set('all');
    this.wfShowArchived.set(false);
  }

  openCreateCredential(): void {
    this.showCreateDropdown = false;
    this.activeTab.set('credentials');
    setTimeout(() => this.credModal?.openCreate(), 0);
  }

  openEditCredential(cred: Credential): void {
    this.credModal?.openEdit(cred);
  }

  onCredentialSaved(): void {
    this.loadCredentials();
  }

  navigateToCredentials(): void {
    this.router.navigate(['/home/credentials']);
  }

  createFolder(): void {
    this.router.navigate(['/home/folders']);
  }

  createDataTable(): void {
    this.router.navigate(['/home/data-tables']);
  }

  // Credential methods
  loadCredentials(): void {
    this.loadingCredentials.set(true);
    this.credentialService.list().subscribe({
      next: (data) => {
        this.credentials.set(data);
        this.loadingCredentials.set(false);
      },
      error: () => this.loadingCredentials.set(false)
    });
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

  credCreatedDate(cred: Credential): string {
    if (!cred.createdAt) return '';
    return new Date(cred.createdAt).toLocaleDateString('en-US', { day: 'numeric', month: 'long' });
  }

  // Execution tab methods
  toggleAutoRefresh(): void {
    this.execAutoRefresh.update(v => !v);
    if (this.execAutoRefresh()) {
      this.startAutoRefresh();
    } else {
      this.stopAutoRefresh();
    }
  }

  startAutoRefresh(): void {
    this.stopAutoRefresh();
    this.execRefreshInterval = setInterval(() => this.loadExecutions(), 5000);
  }

  stopAutoRefresh(): void {
    if (this.execRefreshInterval) {
      clearInterval(this.execRefreshInterval);
      this.execRefreshInterval = null;
    }
  }

  onExecFiltersChange(filters: ExecutionFilters): void {
    this.execFilters.set(filters);
  }

  deleteExecution(id: string, event: Event): void {
    event.stopPropagation();
    this.executionService.delete(id).subscribe({
      next: () => {
        this.executions.update(list => Array.isArray(list) ? list.filter(e => e.id !== id) : []);
      }
    });
  }

  retryExecution(id: string, event: Event): void {
    event.stopPropagation();
    this.executionService.retry(id).subscribe({
      next: () => this.loadExecutions()
    });
  }

  getWorkflowName(workflowId: string): string {
    const wf = this.workflows().find(w => w.id === workflowId);
    return wf?.name || workflowId;
  }

  execRunTime(exec: Execution): string {
    if (!exec.startedAt || !exec.finishedAt) return '-';
    const ms = new Date(exec.finishedAt).getTime() - new Date(exec.startedAt).getTime();
    return this.formatDuration(ms);
  }

  execStartedAt(exec: Execution): string {
    if (!exec.startedAt) return '-';
    return new Date(exec.startedAt).toLocaleString();
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    const s = Math.round(ms / 1000);
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
    return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
  }
}
