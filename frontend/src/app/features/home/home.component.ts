import { Component, OnInit, ViewChild, signal, computed, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { WorkflowService, ExecutionService, CredentialService, ProjectService, SettingsService } from '../../core/services';
import { Workflow, Execution, Credential } from '../../core/models';
import { WorkflowCardComponent } from '../../shared/components/workflow-card/workflow-card.component';
import { VariableListComponent } from '../variables/variable-list.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { CredentialCreateModalComponent } from '../../shared/components/credential-create-modal/credential-create-modal.component';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, KeyRound, Layers, Bot } from 'lucide-angular';
import { CacheListComponent } from '../cache/cache-list.component';
import { ProjectSettingsComponent } from '../project/project-settings.component';
import { WorkflowMoveModalComponent } from '../../shared/components/workflow-move-modal/workflow-move-modal.component';
import { WorkflowShareModalComponent } from '../../shared/components/workflow-share-modal/workflow-share-modal.component';
import {
  ExecutionFilterModalComponent,
  ExecutionFilters,
  defaultExecutionFilters,
  isFilterActive
} from '../../shared/components/execution-filter-modal/execution-filter-modal.component';

@Component({
    selector: 'app-home',
    imports: [
        CommonModule,
        FormsModule,
        WorkflowCardComponent,
        VariableListComponent,
        ConfirmDialogComponent,
        CredentialCreateModalComponent,
        LucideAngularModule,
        ExecutionFilterModalComponent,
        CacheListComponent,
        WorkflowMoveModalComponent,
        WorkflowShareModalComponent,
        ProjectSettingsComponent
    ],
    providers: [{ provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ KeyRound, Layers, Bot }) }],
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  @ViewChild('credModal') credModal!: CredentialCreateModalComponent;
  @ViewChild(CacheListComponent) cacheList!: CacheListComponent;
  @ViewChild('projectSettings') projectSettings!: ProjectSettingsComponent;

  activeTab = signal<'workflows' | 'credentials' | 'executions' | 'variables' | 'caches' | 'agents'>('workflows');

  // Workflow state
  workflows = signal<Workflow[]>([]);
  loadingWorkflows = signal(true);
  searchTerm = signal('');
  sortBy = signal('updatedAt');

  // Insights (pre-aggregated from metrics API)
  executions = signal<Execution[]>([]);
  metricsSummary = signal<{ total: number; success: number; error: number; canceled: number; totalDurationMs: number; finishedCount: number } | null>(null);
  totalExecutions = computed(() => this.metricsSummary()?.total ?? 0);
  failedExecutions = computed(() => this.metricsSummary()?.error ?? 0);
  failureRate = computed(() => {
    const total = this.totalExecutions();
    if (total === 0) return 0;
    return Math.round((this.failedExecutions() / total) * 100);
  });
  avgRunTimeMs = computed(() => {
    const summary = this.metricsSummary();
    if (!summary || summary.finishedCount === 0) return 0;
    return Math.round(summary.totalDurationMs / summary.finishedCount);
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

  // Agent state
  agents = signal<Workflow[]>([]);
  loadingAgents = signal(true);
  agentSearchTerm = signal('');
  agentSortBy = signal('updatedAt');
  filteredAgents = computed(() => {
    const term = this.agentSearchTerm().toLowerCase();
    const sort = this.agentSortBy();
    let agents = this.agents();
    if (term) {
      agents = agents.filter(a => a.name.toLowerCase().includes(term));
    }
    agents = [...agents].sort((a, b) => {
      switch (sort) {
        case 'name': return a.name.localeCompare(b.name);
        case 'nameDesc': return b.name.localeCompare(a.name);
        case 'createdAt': return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
        default: return new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime();
      }
    });
    return agents;
  });

  // Delete
  deleteTarget = signal<Workflow | null>(null);
  showDeleteConfirm = signal(false);
  credDeleteTarget = signal<Credential | null>(null);
  showCredDeleteConfirm = signal(false);
  showCredUnshareConfirm = signal(false);
  showCredShareError = signal(false);
  credShareErrorMessage = signal('');

  // Move
  moveTarget = signal<Workflow | null>(null);
  showMoveModal = signal(false);

  // Share
  shareTarget = signal<Workflow | null>(null);
  showShareModal = signal(false);

  // Credential actions dropdown
  credActionsOpenId = signal<string | null>(null);
  private credActionsCloseTimer: ReturnType<typeof setTimeout> | null = null;

  // Agent actions dropdown
  agentActionsOpenId = signal<string | null>(null);
  agentDropdownStyle: Record<string, string> = {};
  private agentActionsCloseTimer: ReturnType<typeof setTimeout> | null = null;
  private agentScrollListener: (() => void) | null = null;

  // Execution tab state
  execAutoRefresh = signal(false);
  execFilters = signal<ExecutionFilters>(defaultExecutionFilters());
  showExecFilterModal = signal(false);
  loadingExecutions = signal(true);
  execDisplayCount = signal(20);
  execLoadingMore = signal(false);
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
  pagedExecutions = computed(() => this.filteredExecutions().slice(0, this.execDisplayCount()));
  execFilterActive = computed(() => isFilterActive(this.execFilters()));
  private execRefreshInterval: any = null;

  // Projects
  projectMap = new Map<string, string>();
  allProjects = signal<{ id: string; name: string }[]>([]);
  selectedProjectId = signal<string>('');

  // Create dropdown
  showCreateDropdown = false;
  private createDropdownTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private workflowService: WorkflowService,
    private executionService: ExecutionService,
    private credentialService: CredentialService,
    private projectService: ProjectService,
    private settingsService: SettingsService,
    private elRef: ElementRef
  ) {}

  ngOnInit(): void {
    // Set active tab from route param
    const tabFromRoute = this.route.snapshot.paramMap.get('tab');
    if (tabFromRoute && ['workflows', 'credentials', 'executions', 'variables', 'caches', 'agents'].includes(tabFromRoute)) {
      this.activeTab.set(tabFromRoute as any);
    }

    // Auto-open create modals from sidebar navigation, and handle projectId query param
    this.route.queryParams.subscribe(params => {
      if (params['action'] === 'create-credential') {
        this.activeTab.set('credentials');
        setTimeout(() => this.credModal?.openCreate(), 0);
      } else if (params['action'] === 'create-cache') {
        this.activeTab.set('caches');
        setTimeout(() => this.cacheList?.openCreate(), 0);
      }

      // Select project from query param (e.g. after creating a project or clicking sidebar link)
      if (params['projectId'] && params['projectId'] !== this.selectedProjectId()) {
        this.selectedProjectId.set(params['projectId']);
        localStorage.setItem('cwc.selectedProjectId', params['projectId']);
        const knownProject = this.allProjects().some(p => p.id === params['projectId']);
        if (knownProject) {
          this.reloadAllData();
        } else {
          // New project not yet in the list — reload projects (which triggers reloadAllData)
          this.loadProjects();
        }
      }
    });

    // loadProjects sets selectedProjectId and then triggers reloadAllData
    this.loadProjects();
  }

  setTab(tab: 'workflows' | 'credentials' | 'executions' | 'variables' | 'caches' | 'agents'): void {
    this.activeTab.set(tab);
    this.router.navigate(['/home', tab]);
  }

  loadWorkflows(): void {
    this.loadingWorkflows.set(true);
    const projectId = this.selectedProjectId();
    const params: Record<string, string> = { type: 'WORKFLOW' };
    if (projectId) params['projectId'] = projectId;
    this.workflowService.list(params).subscribe({
      next: (data) => {
        this.workflows.set(data);
        this.loadingWorkflows.set(false);
      },
      error: () => this.loadingWorkflows.set(false)
    });
  }

  loadExecutions(): void {
    this.loadingExecutions.set(true);
    const projectId = this.selectedProjectId();
    const params: Record<string, string> = { size: '10000' };
    if (projectId) params['projectId'] = projectId;
    this.executionService.list(params).subscribe({
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

        // Build dropdown list: Personal first, then team projects alphabetically
        const personal = projects.find(p => p.type === 'PERSONAL');
        const team = projects
          .filter(p => p.type === 'TEAM' && p.id)
          .sort((a, b) => a.name.localeCompare(b.name));
        const list: { id: string; name: string }[] = [];
        if (personal?.id) {
          list.push({ id: personal.id, name: 'Personal' });
        }
        team.forEach(p => list.push({ id: p.id!, name: p.name }));
        this.allProjects.set(list);

        // If a project is already selected (e.g. from query param), validate it exists
        const current = this.selectedProjectId();
        if (current && list.some(p => p.id === current)) {
          localStorage.setItem('cwc.selectedProjectId', current);
          this.reloadAllData();
        } else {
          // Try restoring from localStorage
          const stored = localStorage.getItem('cwc.selectedProjectId');
          if (stored && list.some(p => p.id === stored)) {
            this.selectedProjectId.set(stored);
            this.reloadAllData();
          } else if (personal?.id) {
            this.selectedProjectId.set(personal.id);
            localStorage.setItem('cwc.selectedProjectId', personal.id);
            this.reloadAllData();
          }
        }
      }
    });
  }

  onProjectScopeChange(projectId: string): void {
    this.selectedProjectId.set(projectId);
    localStorage.setItem('cwc.selectedProjectId', projectId);
    this.reloadAllData();
  }

  openProjectSettings(): void {
    if (this.selectedProjectId()) {
      this.projectSettings.projectId = this.selectedProjectId();
      this.projectSettings.open();
    }
  }

  onProjectSettingsSaved(): void {
    // Refresh the dropdown list in case name/icon changed
    this.loadProjects();
  }

  onProjectDeleted(): void {
    // Project was deleted — reload projects and select the first available
    this.selectedProjectId.set('');
    localStorage.removeItem('cwc.selectedProjectId');
    this.loadProjects();
  }

  private reloadAllData(): void {
    this.loadWorkflows();
    this.loadCredentials();
    this.loadExecutions();
    this.loadInsightsMetrics();
    this.loadAgents();
  }

  loadAgents(): void {
    this.loadingAgents.set(true);
    const projectId = this.selectedProjectId();
    if (!projectId) {
      this.loadingAgents.set(false);
      return;
    }
    this.workflowService.list({ projectId, type: 'AGENT' }).subscribe({
      next: (data) => {
        this.agents.set(data);
        this.loadingAgents.set(false);
      },
      error: () => this.loadingAgents.set(false)
    });
  }

  createAgent(): void {
    this.showCreateDropdown = false;
    const projectId = this.selectedProjectId();
    this.router.navigate(['/agent/new'], projectId ? { queryParams: { projectId } } : {});
  }

  openAgent(agent: Workflow): void {
    if (agent.id) {
      this.router.navigate(['/agent', agent.id]);
    }
  }

  duplicateAgent(agent: Workflow): void {
    if (!agent.id) return;
    const copy: Partial<Workflow> = {
      name: agent.name + ' (copy)',
      type: 'AGENT',
      icon: agent.icon,
      published: false,
      currentVersion: 0,
      nodes: agent.nodes,
      connections: agent.connections,
      settings: agent.settings,
      projectId: agent.projectId
    };
    this.workflowService.create(copy as Workflow).subscribe({
      next: () => this.loadAgents()
    });
  }

  moveAgent(agent: Workflow): void {
    this.closeAgentDropdown();
    this.moveTarget.set(agent);
    this.showMoveModal.set(true);
  }

  shareAgent(agent: Workflow): void {
    this.closeAgentDropdown();
    if (agent.published) return;
    this.shareTarget.set(agent);
    this.showShareModal.set(true);
  }

  confirmAgentDelete(agent: Workflow): void {
    this.deleteTarget.set(agent);
    this.showDeleteConfirm.set(true);
  }

  onAgentSearchChange(value: string): void {
    this.agentSearchTerm.set(value);
  }

  onAgentSortChange(value: string): void {
    this.agentSortBy.set(value);
  }

  toggleAgentActions(id: string, event: Event): void {
    event.stopPropagation();
    this.cancelAgentActionsClose();
    if (this.agentActionsOpenId() === id) {
      this.closeAgentDropdown();
      return;
    }
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const dropdownHeight = 260;
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < dropdownHeight && rect.top > dropdownHeight;

    this.agentDropdownStyle = {
      position: 'fixed',
      right: (window.innerWidth - rect.right) + 'px',
      ...(openUp
        ? { bottom: (window.innerHeight - rect.top + 4) + 'px' }
        : { top: (rect.bottom + 4) + 'px' }),
      'z-index': '9999'
    };
    this.agentActionsOpenId.set(id);
    this.addAgentScrollListener();
  }

  scheduleAgentActionsClose(): void {
    this.cancelAgentActionsClose();
    this.agentActionsCloseTimer = setTimeout(() => {
      this.closeAgentDropdown();
    }, 400);
  }

  cancelAgentActionsClose(): void {
    if (this.agentActionsCloseTimer) {
      clearTimeout(this.agentActionsCloseTimer);
      this.agentActionsCloseTimer = null;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClickAgent(event: Event): void {
    if (this.agentActionsOpenId() && !this.elRef.nativeElement.querySelector('.agent-card-append')?.contains(event.target)) {
      this.closeAgentDropdown();
    }
  }

  private closeAgentDropdown(): void {
    this.agentActionsOpenId.set(null);
    this.removeAgentScrollListener();
  }

  private addAgentScrollListener(): void {
    this.removeAgentScrollListener();
    const handler = () => this.closeAgentDropdown();
    document.addEventListener('scroll', handler, true);
    this.agentScrollListener = () => document.removeEventListener('scroll', handler, true);
  }

  private removeAgentScrollListener(): void {
    if (this.agentScrollListener) {
      this.agentScrollListener();
      this.agentScrollListener = null;
    }
  }

  agentTimeAgo(agent: Workflow): string {
    const date = agent.updatedAt || agent.createdAt;
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

  loadInsightsMetrics(): void {
    const now = new Date();
    const start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000); // last 7 days
    const projectId = this.selectedProjectId();
    if (!projectId) return;
    const params: Record<string, string> = {
      start: start.toISOString(),
      end: now.toISOString(),
      projectId
    };
    this.executionService.getMetrics(params).subscribe({
      next: (data) => this.metricsSummary.set(data.summary),
      error: () => this.metricsSummary.set(null)
    });
  }

  getProjectName(projectId?: string): string {
    if (!projectId) return '';
    return this.projectMap.get(projectId) || '';
  }

  getCredentialProjectLabel(projectId?: string): string {
    if (!projectId) return 'Personal';
    const proj = this.allProjects().find(p => p.id === projectId);
    return proj?.name || 'Personal';
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
    const projectId = this.selectedProjectId();
    this.router.navigate(['/workflow/new'], projectId ? { queryParams: { projectId } } : {});
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

  onEnableMcp(workflow: Workflow): void {
    if (!workflow.id) return;
    const enabled = !!workflow.mcpEnabled || (workflow.tags || []).some(t => t.name === 'mcp');
    this.settingsService.updateMcpWorkflow(workflow.id, { mcpEnabled: !enabled } as any).subscribe({
      next: () => this.loadWorkflows()
    });
  }

  onEnableSwagger(workflow: Workflow): void {
    if (!workflow.id) return;
    const enabled = !!workflow.swaggerEnabled || (workflow.tags || []).some(t => t.name === 'swagger');
    this.settingsService.updateSwaggerWorkflow(workflow.id, { swaggerEnabled: !enabled } as any).subscribe({
      next: () => this.loadWorkflows()
    });
  }

  onMoveWorkflow(workflow: Workflow): void {
    this.moveTarget.set(workflow);
    this.showMoveModal.set(true);
  }

  onMoveConfirmed(projectId: string): void {
    const target = this.moveTarget();
    if (!target?.id) return;
    this.workflowService.move(target.id, projectId).subscribe({
      next: () => {
        this.showMoveModal.set(false);
        this.moveTarget.set(null);
        if (target.type === 'AGENT') {
          this.loadAgents();
        } else {
          this.loadWorkflows();
        }
      }
    });
  }

  onMoveCancelled(): void {
    this.showMoveModal.set(false);
    this.moveTarget.set(null);
  }

  onShareWorkflow(workflow: Workflow): void {
    if (workflow.published) return;
    this.shareTarget.set(workflow);
    this.showShareModal.set(true);
  }

  onShareClosed(): void {
    this.showShareModal.set(false);
    this.shareTarget.set(null);
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
          if (target.type === 'AGENT') {
            this.agents.update(a => a.filter(w => w.id !== target.id));
          } else {
            this.workflows.update(wfs => wfs.filter(w => w.id !== target.id));
          }
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

  createCache(): void {
    this.showCreateDropdown = false;
    this.activeTab.set('caches');
    this.router.navigate(['/home', 'caches']);
    setTimeout(() => this.cacheList?.openCreate(), 0);
  }

  // Credential methods
  loadCredentials(): void {
    this.loadingCredentials.set(true);
    const projectId = this.selectedProjectId();
    const source = projectId
      ? this.credentialService.listByProject(projectId)
      : this.credentialService.list();
    source.subscribe({
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
    this.cancelCredActionsClose();
    this.credActionsOpenId.set(this.credActionsOpenId() === id ? null : id);
  }

  scheduleCredActionsClose(): void {
    this.cancelCredActionsClose();
    this.credActionsCloseTimer = setTimeout(() => {
      this.credActionsOpenId.set(null);
    }, 400);
  }

  cancelCredActionsClose(): void {
    if (this.credActionsCloseTimer) {
      clearTimeout(this.credActionsCloseTimer);
      this.credActionsCloseTimer = null;
    }
  }

  confirmCredDelete(cred: Credential, event: Event): void {
    event.stopPropagation();
    this.credActionsOpenId.set(null);
    this.credDeleteTarget.set(cred);

    const currentProjectId = this.selectedProjectId();
    const isOwner = !cred.projectId || cred.projectId === currentProjectId;

    if (!isOwner) {
      // Shared credential — offer to remove the share instead
      this.showCredUnshareConfirm.set(true);
    } else if (cred.sharedWithProjectIds?.length) {
      // Owner but credential is shared — block deletion and show error
      const projectNames = cred.sharedWithProjectIds
        .map(id => this.allProjects().find(p => p.id === id)?.name || id)
        .join(', ');
      this.credShareErrorMessage.set(
        `Cannot delete '${cred.name}' because it is shared with: ${projectNames}. Revoke sharing access first.`
      );
      this.showCredShareError.set(true);
    } else {
      // Owner, no shares — normal delete
      this.showCredDeleteConfirm.set(true);
    }
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

  onCredUnshareConfirmed(): void {
    const target = this.credDeleteTarget();
    const currentProjectId = this.selectedProjectId();
    if (target?.id && currentProjectId) {
      this.credentialService.unshareCredential(target.id, currentProjectId).subscribe({
        next: () => {
          this.credentials.update(cs => cs.filter(c => c.id !== target.id));
          this.showCredUnshareConfirm.set(false);
          this.credDeleteTarget.set(null);
        }
      });
    }
  }

  onCredDeleteCancelled(): void {
    this.showCredDeleteConfirm.set(false);
    this.showCredUnshareConfirm.set(false);
    this.showCredShareError.set(false);
    this.credDeleteTarget.set(null);
  }

  isSharedCredential(cred: Credential): boolean {
    return !!cred.projectId && cred.projectId !== this.selectedProjectId();
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

  refreshExecutions(): void {
    this.loadExecutions();
  }

  onExecScroll(event: Event): void {
    const el = event.target as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 100) {
      const current = this.execDisplayCount();
      const total = this.filteredExecutions().length;
      if (current < total) {
        this.execDisplayCount.set(Math.min(current + 20, total));
      }
    }
  }

  onExecFiltersChange(filters: ExecutionFilters): void {
    this.execFilters.set(filters);
    this.execDisplayCount.set(20);
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
