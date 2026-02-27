import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { SettingsService, UsageStats, UserInfo, ApiKeyInfo, AiSettings, McpSettings, McpWorkflow, McpEndpoint, McpClient, McpParameter } from '../../core/services/settings.service';
import { NodeTypeService } from '../../core/services/node-type.service';
import { NodeTypeDescription } from '../../core/models';
import { McpParamEditorModalComponent } from '../../shared/components/mcp-param-editor-modal/mcp-param-editor-modal.component';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, McpParamEditorModalComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit, OnDestroy {
  activeSection = 'personal';
  private routeSub?: Subscription;

  // Usage
  usage: UsageStats | null = null;

  // Personal
  personalForm = { firstName: '', lastName: '', email: '', currentPassword: '', newPassword: '' };
  personalSaving = false;

  // Users
  users: UserInfo[] = [];
  showAddUser = false;
  newUser = { email: '', firstName: '', lastName: '', password: '', role: 'editor' };
  addingUser = false;

  // API Keys
  apiKeys: ApiKeyInfo[] = [];
  showCreateKey = false;
  newKeyLabel = '';
  creatingKey = false;
  createdKeyValue: string | null = null;
  copiedKey = false;

  // AI / Chat
  aiSettings: AiSettings = { provider: 'openai', apiKey: '', model: 'gpt-4o-mini', baseUrl: null, enabled: false };
  aiSettingsSaving = false;

  // MCP
  mcpSettings: McpSettings | null = null;
  mcpWorkflows: McpWorkflow[] = [];
  mcpEndpoints: McpEndpoint[] = [];
  mcpClients: McpClient[] = [];
  mcpSaving = false;
  mcpActiveTab = 'workflows';
  showConnectionDetailsModal = false;
  showAddEndpoint = false;
  newEndpoint = { name: '', transport: 'SSE', path: '' };
  editingWorkflowId: string | null = null;
  editDescriptionValue = '';
  mcpWorkflowMenuId: string | null = null;
  mcpMenuStyle: Record<string, string> = {};
  private mcpMenuCloseTimer: ReturnType<typeof setTimeout> | null = null;
  private mcpMenuScrollListener: (() => void) | null = null;

  // Param editor modal
  showParamEditorModal = false;
  paramEditorWorkflow: McpWorkflow | null = null;

  get showMcpProjectColumn(): boolean {
    return this.mcpWorkflows.some(wf => !!wf.projectId);
  }

  // Community Nodes
  nodeTypes: NodeTypeDescription[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private settingsService: SettingsService,
    private nodeTypeService: NodeTypeService
  ) {}

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      this.activeSection = params.get('section') || 'usage';
      this.loadSectionData();
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  private loadSectionData(): void {
    switch (this.activeSection) {
      case 'personal':
        this.settingsService.listUsers().subscribe({
          next: users => {
            const owner = users.find(u => u.role === 'owner') || users[0];
            if (owner) {
              this.personalForm.firstName = owner.firstName || '';
              this.personalForm.lastName = owner.lastName || '';
              this.personalForm.email = owner.email || '';
            }
          }
        });
        break;
      case 'users':
        this.loadUsers();
        break;
      case 'api':
        this.loadApiKeys();
        break;
      case 'chat':
        this.loadAiSettings();
        break;
      case 'mcp':
        this.loadMcpSettings();
        break;
      case 'community-nodes':
        this.nodeTypeService.getAll().subscribe({
          next: types => this.nodeTypes = types
        });
        break;
    }
  }

  private loadUsers(): void {
    this.settingsService.listUsers().subscribe({
      next: users => this.users = users,
      error: () => this.users = []
    });
  }

  private loadApiKeys(): void {
    this.settingsService.listApiKeys().subscribe({
      next: keys => this.apiKeys = keys,
      error: () => this.apiKeys = []
    });
  }

  // Personal actions
  savePersonal(): void {
    this.personalSaving = true;
    this.settingsService.listUsers().subscribe({
      next: users => {
        const owner = users.find(u => u.role === 'owner') || users[0];
        if (!owner) { this.personalSaving = false; return; }
        const data: any = {
          firstName: this.personalForm.firstName,
          lastName: this.personalForm.lastName,
          email: this.personalForm.email
        };
        if (this.personalForm.newPassword) {
          data.password = this.personalForm.newPassword;
        }
        this.settingsService.updateUser(owner.id, data).subscribe({
          next: () => {
            this.personalSaving = false;
            this.personalForm.currentPassword = '';
            this.personalForm.newPassword = '';
          },
          error: () => this.personalSaving = false
        });
      }
    });
  }

  // User actions
  addUser(): void {
    if (!this.newUser.email || !this.newUser.password) return;
    this.addingUser = true;
    this.settingsService.createUser(this.newUser).subscribe({
      next: () => {
        this.addingUser = false;
        this.showAddUser = false;
        this.newUser = { email: '', firstName: '', lastName: '', password: '', role: 'editor' };
        this.loadUsers();
      },
      error: () => this.addingUser = false
    });
  }

  deleteUser(id: string): void {
    this.settingsService.deleteUser(id).subscribe({
      next: () => this.loadUsers()
    });
  }

  // API Key actions
  createApiKey(): void {
    if (!this.newKeyLabel) return;
    this.creatingKey = true;
    this.settingsService.createApiKey(this.newKeyLabel).subscribe({
      next: key => {
        this.creatingKey = false;
        this.createdKeyValue = key.apiKey || null;
        this.newKeyLabel = '';
        this.showCreateKey = false;
        this.loadApiKeys();
      },
      error: () => this.creatingKey = false
    });
  }

  copyApiKey(): void {
    if (this.createdKeyValue) {
      navigator.clipboard.writeText(this.createdKeyValue);
      this.copiedKey = true;
      setTimeout(() => this.copiedKey = false, 2000);
    }
  }

  dismissCreatedKey(): void {
    this.createdKeyValue = null;
    this.copiedKey = false;
  }

  deleteApiKey(id: string): void {
    this.settingsService.deleteApiKey(id).subscribe({
      next: () => this.loadApiKeys()
    });
  }

  getRoleLabel(role: string): string {
    switch (role) {
      case 'owner': return 'Owner';
      case 'admin': return 'Admin';
      case 'editor': return 'Editor';
      case 'viewer': return 'Viewer';
      default: return role;
    }
  }

  // AI Settings
  private loadAiSettings(): void {
    this.settingsService.getAiSettings().subscribe({
      next: settings => this.aiSettings = settings,
      error: () => {}
    });
  }

  saveAiSettings(): void {
    this.aiSettingsSaving = true;
    this.settingsService.updateAiSettings(this.aiSettings).subscribe({
      next: settings => {
        this.aiSettings = settings;
        this.aiSettingsSaving = false;
      },
      error: () => this.aiSettingsSaving = false
    });
  }

  showBaseUrl(): boolean {
    return ['ollama', 'azure-openai', 'bedrock'].includes(this.aiSettings.provider);
  }

  // MCP Settings
  private loadMcpSettings(): void {
    this.settingsService.getMcpSettings().subscribe({
      next: settings => {
        this.mcpSettings = settings;
        this.mcpEndpoints = settings.endpoints || [];
      },
      error: () => {}
    });
    this.settingsService.getMcpWorkflows().subscribe({
      next: workflows => this.mcpWorkflows = workflows,
      error: () => this.mcpWorkflows = []
    });
  }

  toggleMcp(): void {
    if (!this.mcpSettings) return;
    this.mcpSaving = true;
    this.settingsService.updateMcpSettings({ enabled: !this.mcpSettings.enabled }).subscribe({
      next: settings => {
        this.mcpSettings = settings;
        this.mcpEndpoints = settings.endpoints || [];
        this.mcpSaving = false;
      },
      error: () => this.mcpSaving = false
    });
  }

  onMcpTabChange(tab: string): void {
    this.mcpActiveTab = tab;
    if (tab === 'clients') {
      this.loadMcpClients();
    }
  }

  // Endpoint actions
  createMcpEndpoint(): void {
    if (!this.newEndpoint.name || !this.newEndpoint.path) return;
    this.settingsService.createMcpEndpoint(this.newEndpoint).subscribe({
      next: () => {
        this.showAddEndpoint = false;
        this.newEndpoint = { name: '', transport: 'SSE', path: '' };
        this.loadMcpSettings();
      }
    });
  }

  deleteEndpoint(id: string): void {
    this.settingsService.deleteMcpEndpoint(id).subscribe({
      next: () => this.loadMcpSettings()
    });
  }

  copyEndpointUrl(url: string): void {
    navigator.clipboard.writeText(url);
  }

  // Client actions
  loadMcpClients(): void {
    this.settingsService.listMcpClients().subscribe({
      next: clients => this.mcpClients = clients,
      error: () => this.mcpClients = []
    });
  }

  // Workflow actions
  toggleWorkflowMcp(workflow: McpWorkflow): void {
    this.settingsService.updateMcpWorkflow(workflow.id, { mcpEnabled: !workflow.mcpEnabled } as any).subscribe({
      next: () => {
        workflow.mcpEnabled = !workflow.mcpEnabled;
      }
    });
  }

  toggleWorkflowMenu(workflowId: string, event: MouseEvent): void {
    this.cancelMcpMenuClose();
    if (this.mcpWorkflowMenuId === workflowId) {
      this.closeMcpMenu();
      return;
    }
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const dropdownHeight = 100;
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < dropdownHeight && rect.top > dropdownHeight;

    this.mcpMenuStyle = {
      position: 'fixed',
      right: (window.innerWidth - rect.right) + 'px',
      ...(openUp
        ? { bottom: (window.innerHeight - rect.top + 4) + 'px' }
        : { top: (rect.bottom + 4) + 'px' }),
      'z-index': '9999'
    };
    this.mcpWorkflowMenuId = workflowId;
    this.addMcpMenuScrollListener();
  }

  scheduleMcpMenuClose(): void {
    this.cancelMcpMenuClose();
    this.mcpMenuCloseTimer = setTimeout(() => this.closeMcpMenu(), 400);
  }

  cancelMcpMenuClose(): void {
    if (this.mcpMenuCloseTimer) {
      clearTimeout(this.mcpMenuCloseTimer);
      this.mcpMenuCloseTimer = null;
    }
  }

  private closeMcpMenu(): void {
    this.mcpWorkflowMenuId = null;
    this.removeMcpMenuScrollListener();
  }

  private addMcpMenuScrollListener(): void {
    this.removeMcpMenuScrollListener();
    const handler = () => this.closeMcpMenu();
    document.addEventListener('scroll', handler, true);
    this.mcpMenuScrollListener = () => document.removeEventListener('scroll', handler, true);
  }

  private removeMcpMenuScrollListener(): void {
    if (this.mcpMenuScrollListener) {
      this.mcpMenuScrollListener();
      this.mcpMenuScrollListener = null;
    }
  }

  startEditDescription(workflow: McpWorkflow): void {
    this.editingWorkflowId = workflow.id;
    this.editDescriptionValue = workflow.mcpDescription || workflow.description || '';
    this.mcpWorkflowMenuId = null;
  }

  saveDescription(workflow: McpWorkflow): void {
    this.settingsService.updateMcpWorkflow(workflow.id, { mcpDescription: this.editDescriptionValue } as any).subscribe({
      next: () => {
        workflow.mcpDescription = this.editDescriptionValue;
        this.editingWorkflowId = null;
      }
    });
  }

  cancelEditDescription(): void {
    this.editingWorkflowId = null;
  }

  // --- Param editor ---

  openParamEditor(wf: McpWorkflow): void {
    this.paramEditorWorkflow = wf;
    this.showParamEditorModal = true;
    this.mcpWorkflowMenuId = null;
  }

  onParamEditorSaved(params: McpParameter[]): void {
    if (!this.paramEditorWorkflow) return;
    const validParams = params.filter(p => p.name.trim());
    this.settingsService.updateMcpWorkflow(this.paramEditorWorkflow.id, { mcpInputSchema: validParams } as any).subscribe({
      next: () => {
        if (this.paramEditorWorkflow) {
          this.paramEditorWorkflow.mcpInputSchema = validParams.length > 0 ? validParams : null;
        }
        this.showParamEditorModal = false;
        this.paramEditorWorkflow = null;
      }
    });
  }

  onModalBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('mcp-modal-backdrop')) {
      this.showConnectionDetailsModal = false;
    }
  }
}
