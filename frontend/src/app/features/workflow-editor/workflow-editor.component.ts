import { Component, HostListener, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Subscription } from 'rxjs';
import { WorkflowService, WebSocketService, ExecutionService } from '../../core/services';
import { WorkflowEditorStore } from '../../core/state/workflow-editor.store';
import { NodeTypeStore } from '../../core/state/node-type.store';
import { ExecutionsSidebarComponent } from './components/executions-sidebar/executions-sidebar.component';
import { ToolbarComponent, ToolbarAction } from './components/toolbar/toolbar.component';
import { NodePaletteComponent, NodeClickedWithAction } from './components/node-palette/node-palette.component';
import { ReactFlowWrapperComponent } from './components/canvas/react-flow-wrapper.component';
import { ParameterPanelComponent } from './components/parameter-panel/parameter-panel.component';
import { EditorDrawerComponent } from './components/editor-drawer/editor-drawer.component';
import { PublishModalComponent } from './components/publish-modal/publish-modal.component';
import { DescriptionModalComponent } from './components/description-modal/description-modal.component';
import { ImportUriModalComponent } from './components/import-uri-modal/import-uri-modal.component';
import { SettingsModalComponent, WorkflowSettings } from './components/settings-modal/settings-modal.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  ExecutionFilterModalComponent,
  ExecutionFilters,
  defaultExecutionFilters
} from '../../shared/components/execution-filter-modal/execution-filter-modal.component';
import { NodeTypeDescription, Workflow, WorkflowNode, Execution } from '../../core/models';

@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [
    CommonModule,
    ToolbarComponent,
    NodePaletteComponent,
    ReactFlowWrapperComponent,
    ParameterPanelComponent,
    EditorDrawerComponent,
    PublishModalComponent,
    DescriptionModalComponent,
    ImportUriModalComponent,
    SettingsModalComponent,
    ConfirmDialogComponent,
    ExecutionsSidebarComponent,
    ExecutionFilterModalComponent
  ],
  templateUrl: './workflow-editor.component.html',
  styleUrl: './workflow-editor.component.scss'
})
export class WorkflowEditorComponent implements OnInit, OnDestroy {
  @ViewChild(ReactFlowWrapperComponent) canvasWrapper!: ReactFlowWrapperComponent;
  @ViewChild(NodePaletteComponent) nodePalette!: NodePaletteComponent;

  showPalette = false;
  showPublishModal = false;
  showDescriptionModal = false;
  showImportUriModal = false;
  showSettingsModal = false;
  showArchiveConfirm = false;
  availableWorkflows: { id: string; name: string }[] = [];
  drawerExpandedEditor = false;
  drawerExpandedExecutions = false;
  activeTab: 'editor' | 'executions' = 'editor';
  pendingConnection: { sourceNodeId: string; sourceHandleId: string; isTargetHandle?: boolean } | null = null;
  selectedExecutionId: string | null = null;
  showExecFilterModal = false;
  execSidebarFilters: ExecutionFilters = defaultExecutionFilters();
  viewingExecution: Execution | null = null;
  executionWorkflow: Workflow | null = null;
  executionDataById: Record<string, any> | null = null;
  webhookTestData: Record<string, any> = {};
  private executionSub?: Subscription;
  private currentExecutionId: string | null = null;
  private autoSaveInterval: ReturnType<typeof setInterval> | null = null;
  private readonly AUTO_SAVE_INTERVAL = 3_000;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private workflowService: WorkflowService,
    private executionService: ExecutionService,
    private wsService: WebSocketService,
    public store: WorkflowEditorStore,
    public nodeTypeStore: NodeTypeStore
  ) {}

  ngOnInit(): void {
    this.nodeTypeStore.loadNodeTypes();

    const id = this.route.snapshot.paramMap.get('id');
    const qp = this.route.snapshot.queryParams;

    if (id) {
      this.workflowService.get(id).subscribe({
        next: (workflow) => {
          this.store.loadWorkflow(workflow);

          // If navigated with tab=executions&executionId=..., open that execution
          if (qp['tab'] === 'executions') {
            this.activeTab = 'executions';
            if (qp['executionId']) {
              this.onExecutionSelected(qp['executionId']);
            }
          }
        },
        error: () => this.router.navigate(['/home/workflows'])
      });
    } else {
      this.store.createNew();
      const projectId = this.route.snapshot.queryParamMap.get('projectId');
      if (projectId) {
        const wf = this.store.workflow();
        if (wf) {
          this.store.workflow.set({ ...wf, projectId });
        }
      }
    }

    this.startAutoSaveTimer();
  }

  private replaceUrlOnFirstSave = (saved: { id?: string }) => {
    if (saved.id) {
      this.location.replaceState('/workflow/' + saved.id);
    }
  };

  private startAutoSaveTimer(): void {
    this.stopAutoSaveTimer();
    this.autoSaveInterval = setInterval(() => {
      if (this.store.isDirty() && !this.store.isSaving() && !this.store.isExecuting()) {
        this.store.saveWorkflow(this.replaceUrlOnFirstSave);
      }
    }, this.AUTO_SAVE_INTERVAL);
  }

  private stopAutoSaveTimer(): void {
    if (this.autoSaveInterval !== null) {
      clearInterval(this.autoSaveInterval);
      this.autoSaveInterval = null;
    }
  }

  togglePalette(): void {
    this.showPalette = !this.showPalette;
    if (this.showPalette) {
      setTimeout(() => {
        if (this.nodePalette) {
          this.nodePalette.triggerOnly.set(false);
          this.nodePalette.aiOutputTypeFilter.set(null);
          this.nodePalette.focusSearch();
        }
      }, 260);
    } else {
      this.pendingConnection = null;
    }
  }

  openPaletteWithTrigger(): void {
    this.showPalette = true;
    setTimeout(() => {
      if (this.nodePalette) {
        this.nodePalette.triggerOnly.set(true);
        this.nodePalette.searchTerm.set('');
        this.nodePalette.focusSearch();
      }
    }, 260);
  }

  get drawerExpanded(): boolean {
    return this.activeTab === 'executions' ? this.drawerExpandedExecutions : this.drawerExpandedEditor;
  }

  set drawerExpanded(value: boolean) {
    if (this.activeTab === 'executions') {
      this.drawerExpandedExecutions = value;
    } else {
      this.drawerExpandedEditor = value;
    }
  }

  toggleDrawer(): void {
    this.drawerExpanded = !this.drawerExpanded;
  }

  private isInputFocused(event: KeyboardEvent): boolean {
    const target = event.target as HTMLElement;
    return target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable;
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    // Undo: Ctrl+Z (not Shift)
    if ((event.ctrlKey || event.metaKey) && event.key === 'z' && !event.shiftKey) {
      if (!this.isInputFocused(event)) {
        event.preventDefault();
        this.store.undo();
      }
    }

    // Redo: Ctrl+Y or Ctrl+Shift+Z
    if ((event.ctrlKey || event.metaKey) && (event.key === 'y' || (event.key === 'Z' && event.shiftKey) || (event.key === 'z' && event.shiftKey))) {
      if (!this.isInputFocused(event)) {
        event.preventDefault();
        this.store.redo();
      }
    }

    if (event.key === 'Tab' && this.activeTab === 'editor') {
      event.preventDefault();
      this.togglePalette();
    }

    if ((event.ctrlKey || event.metaKey) && event.key === 'j') {
      event.preventDefault();
      this.toggleDrawer();
    }

    if (event.key === 'l' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      if (!this.isInputFocused(event)) {
        this.toggleDrawer();
      }
    }
  }

  onPaletteNodeClicked(nodeType: NodeTypeDescription): void {
    const newNodeId = this.canvasWrapper.addNodeAtViewportCenter(nodeType.type, nodeType.displayName, nodeType.version);
    if (this.pendingConnection) {
      this.addConnectionFromPending(newNodeId);
      this.pendingConnection = null;
    }
    // Once a trigger node is placed, dismiss the trigger-only filter
    if (nodeType.isTrigger && this.nodePalette?.triggerOnly()) {
      this.nodePalette.triggerOnly.set(false);
    }
    this.closePalette();
  }

  onPaletteNodeClickedWithAction(event: NodeClickedWithAction): void {
    const { nodeType, initialParams } = event;
    const newNodeId = this.canvasWrapper.addNodeAtViewportCenter(
      nodeType.type, nodeType.displayName, nodeType.version, initialParams
    );
    if (this.pendingConnection) {
      this.addConnectionFromPending(newNodeId);
      this.pendingConnection = null;
    }
    if (nodeType.isTrigger && this.nodePalette?.triggerOnly()) {
      this.nodePalette.triggerOnly.set(false);
    }
    this.closePalette();
  }

  private closePalette(): void {
    this.showPalette = false;
    this.nodePalette?.aiOutputTypeFilter.set(null);
  }

  onOutputHandleDoubleClicked(event: { nodeId: string; handleId: string }): void {
    this.pendingConnection = { sourceNodeId: event.nodeId, sourceHandleId: event.handleId };

    // Decode handle type from "ai_languageModel:0" → "ai_languageModel"
    let connectionType = 'main';
    if (event.handleId.includes(':')) {
      connectionType = event.handleId.substring(0, event.handleId.lastIndexOf(':'));
    }

    // Determine if the clicked handle is an AI type
    const isAiHandle = connectionType.startsWith('ai_');

    // Check if the clicked handle is an INPUT (target) on the source node
    // In that case we need to show nodes with matching OUTPUT and swap the connection direction
    if (isAiHandle) {
      const sourceNode = this.store.nodes().find(n => n.id === event.nodeId);
      const sourceType = sourceNode ? this.nodeTypeStore.getByType(sourceNode.type) : null;
      const isInputHandle = sourceType?.inputs?.some(i => i.type === connectionType) ?? false;
      if (isInputHandle) {
        // Store a flag so addConnectionFromPending knows to swap direction
        this.pendingConnection = {
          sourceNodeId: event.nodeId,
          sourceHandleId: event.handleId,
          isTargetHandle: true,
        };
      }
    }

    this.showPalette = true;
    setTimeout(() => {
      if (this.nodePalette) {
        this.nodePalette.triggerOnly.set(false);
        this.nodePalette.searchTerm.set('');
        this.nodePalette.aiOutputTypeFilter.set(isAiHandle ? connectionType : null);
        this.nodePalette.focusSearch();
      }
    }, 260);
  }

  private addConnectionFromPending(newNodeId: string): void {
    if (!this.pendingConnection) return;
    const { sourceNodeId, sourceHandleId, isTargetHandle } = this.pendingConnection;

    // Decode handle ID: "type:index" or legacy "name"
    let connectionType = 'main';
    let outputIndex = 0;
    if (sourceHandleId.includes(':')) {
      const sep = sourceHandleId.lastIndexOf(':');
      connectionType = sourceHandleId.substring(0, sep);
      outputIndex = parseInt(sourceHandleId.substring(sep + 1), 10) || 0;
    } else {
      // Legacy: find by output name
      const sourceNode = this.store.nodes().find(n => n.id === sourceNodeId);
      if (!sourceNode) return;
      const sourceType = this.nodeTypeStore.getByType(sourceNode.type);
      const outputs = sourceType?.outputs || [{ name: 'main', type: 'main' }];
      const idx = outputs.findIndex(o => o.name === sourceHandleId);
      if (idx >= 0) {
        connectionType = outputs[idx].type || 'main';
        outputIndex = idx;
      }
    }

    // When the clicked handle was a target/input (e.g. AI Agent's bottom "Model" handle),
    // the new sub-node is the source and the clicked node is the target.
    // Connection direction: newNode (sub-node) → sourceNode (parent).
    const fromNodeId = isTargetHandle ? newNodeId : sourceNodeId;
    const toNodeId = isTargetHandle ? sourceNodeId : newNodeId;
    const fromIndex = isTargetHandle ? 0 : outputIndex;

    const connections = JSON.parse(JSON.stringify(this.store.connections()));
    if (!connections[fromNodeId]) {
      connections[fromNodeId] = {};
    }
    if (!connections[fromNodeId][connectionType]) {
      connections[fromNodeId][connectionType] = [];
    }
    // Ensure the array has enough entries for the output index
    while (connections[fromNodeId][connectionType].length <= fromIndex) {
      connections[fromNodeId][connectionType].push([]);
    }
    connections[fromNodeId][connectionType][fromIndex].push({
      node: toNodeId,
      type: connectionType,
      index: isTargetHandle ? outputIndex : 0,
    });

    this.store.updateConnections(connections);
  }

  onNodeSelected(nodeId: string | null): void {
    if (this.activeTab === 'executions') {
      // In executions mode, single-click opens the parameter panel
      this.store.selectNode(nodeId);
    } else {
      // In editor mode, single-click only closes the panel (pane click); double-click opens it
      if (nodeId === null) {
        this.store.selectNode(null);
      }
    }
  }

  onSave(): void {
    this.store.saveWorkflow(this.replaceUrlOnFirstSave);
    this.startAutoSaveTimer();
  }

  onExecute(): void {
    const wf = this.store.workflow();
    if (!wf?.id) {
      this.store.saveWorkflow(this.replaceUrlOnFirstSave);
      return;
    }
    this.store.setIsExecuting(true);
    this.store.setExecutionData(null);
    this.drawerExpanded = true;
    this.executionSub?.unsubscribe();
    this.executionSub = this.workflowService.run(wf.id).subscribe({
      next: (result) => {
        const executionId = result.executionId;
        if (!executionId) {
          this.store.setIsExecuting(false);
          return;
        }
        this.currentExecutionId = executionId;
        const topic = `/topic/execution/${executionId}`;
        this.wsService.subscribe(topic, (message) => {
          const event = JSON.parse(message.body);
          if (event.event === 'nodeFinished') {
            // Incrementally update execution data as each node finishes
            const current = this.store.executionData() || {};
            this.store.setExecutionData({
              ...current,
              [event.nodeId]: event.data
            });
          } else if (event.event === 'executionFinished') {
            this.store.setExecutionData(event.data);
            this.store.setIsExecuting(false);
            this.wsService.unsubscribe(topic);
            this.currentExecutionId = null;
          }
        });
      },
      error: () => {
        this.store.setIsExecuting(false);
      }
    });
  }

  onExecuteFromNode(nodeId: string): void {
    // For now, execute the full workflow (backend can be enhanced to support partial execution)
    this.onExecute();
  }

  onStopExecution(): void {
    if (this.currentExecutionId) {
      this.workflowService.stopExecution(this.currentExecutionId).subscribe();
      this.wsService.unsubscribe(`/topic/execution/${this.currentExecutionId}`);
      this.currentExecutionId = null;
    }
    this.executionSub?.unsubscribe();
    this.store.setIsExecuting(false);
  }

  ngOnDestroy(): void {
    if (this.currentExecutionId) {
      this.wsService.unsubscribe(`/topic/execution/${this.currentExecutionId}`);
    }
    this.executionSub?.unsubscribe();
    this.stopAutoSaveTimer();
  }

  onTabChanged(tab: 'editor' | 'executions'): void {
    this.activeTab = tab;
    if (tab === 'editor') {
      this.clearExecutionView();
    }
  }

  onExecutionSelected(executionId: string): void {
    this.selectedExecutionId = executionId;
    this.executionService.get(executionId).subscribe({
      next: (exec) => {
        this.viewingExecution = exec;
        const wd = exec.workflowData;
        if (wd) {
          this.executionWorkflow = {
            id: wd.id,
            name: wd.name,
            published: false,
            currentVersion: 0,
            nodes: wd.nodes || [],
            connections: wd.connections || {},
          };

          // Remap resultData.runData from node-name keys to node-ID keys
          const runData = exec.resultData?.runData || {};
          const nameToId = new Map<string, string>();
          for (const node of (wd.nodes || [])) {
            nameToId.set(node.name, node.id);
          }
          const dataById: Record<string, any> = {};
          for (const [nodeName, data] of Object.entries(runData)) {
            const nodeId = nameToId.get(nodeName);
            if (nodeId) {
              dataById[nodeId] = data;
            }
          }
          this.executionDataById = dataById;
        }
      }
    });
  }

  /** Returns the selected node from the correct source (execution snapshot or live workflow). */
  get activeSelectedNode(): WorkflowNode | null {
    const nodeId = this.store.selectedNodeId();
    if (!nodeId) return null;
    if (this.activeTab === 'executions' && this.executionWorkflow) {
      return this.executionWorkflow.nodes.find(n => n.id === nodeId) || null;
    }
    return this.store.selectedNode();
  }

  private clearExecutionView(): void {
    this.selectedExecutionId = null;
    this.viewingExecution = null;
    this.executionWorkflow = null;
    this.executionDataById = null;
  }

  onWebhookTestData(event: { nodeId: string; data: any }): void {
    this.webhookTestData = { ...this.webhookTestData, [event.nodeId]: event.data };
  }

  onNodeExecuted(event: { nodeId: string; data: any }): void {
    const current = this.store.executionData() || {};
    if (event.data === null) {
      // Clear this node's data (execution starting)
      const { [event.nodeId]: _, ...rest } = current;
      this.store.setExecutionData(Object.keys(rest).length > 0 ? rest : null);
    } else {
      this.store.setExecutionData({ ...current, [event.nodeId]: event.data });
    }
  }

  toastMessage = '';
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastMessage = '', 3000);
  }

  onPublishClicked(): void {
    const wf = this.store.workflow();
    if (!wf?.id) {
      // Save first, then show modal
      this.store.saveWorkflow((saved) => {
        this.replaceUrlOnFirstSave(saved);
        this.showPublishModal = true;
      });
      return;
    }
    if (wf.published && !wf.versionIsDirty && !this.store.isDirty()) {
      this.showToast('The latest workflow changes have already been published.');
      return;
    }
    if (this.store.isDirty()) {
      this.store.saveWorkflow(() => {
        this.showPublishModal = true;
      });
      return;
    }
    this.showPublishModal = true;
  }

  onPublishConfirmed(event: { versionName: string; description: string }): void {
    const wf = this.store.workflow();
    if (!wf?.id) return;
    this.workflowService.publish(wf.id, event).subscribe({
      next: (updated) => {
        this.store.workflow.set(updated);
        this.showPublishModal = false;
      },
      error: (err) => {
        console.error('Failed to publish workflow:', err);
        this.showPublishModal = false;
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/home/workflows']);
  }

  // --- Menu actions ---

  onMenuAction(action: ToolbarAction): void {
    switch (action) {
      case 'editDescription':
        this.showDescriptionModal = true;
        break;
      case 'duplicate':
        this.onDuplicate();
        break;
      case 'download':
        this.onDownload();
        break;
      case 'importFromUri':
        this.showImportUriModal = true;
        break;
      case 'importFromFile':
        this.onImportFromFile();
        break;
      case 'pushToGit':
        this.onPushToGit();
        break;
      case 'settings':
        this.onOpenSettings();
        break;
      case 'unpublish':
        this.onUnpublish();
        break;
      case 'archive':
        this.showArchiveConfirm = true;
        break;
    }
  }

  onDescriptionSaved(description: string): void {
    this.store.updateWorkflowDescription(description);
    this.showDescriptionModal = false;
  }

  private onDuplicate(): void {
    const wf = this.store.workflow();
    if (!wf?.id) return;
    this.workflowService.duplicate(wf.id).subscribe({
      next: (dup) => {
        this.router.navigate(['/workflow', dup.id]);
      }
    });
  }

  private onDownload(): void {
    const wf = this.store.workflow();
    if (!wf) return;
    const exportData = {
      name: wf.name,
      description: wf.description,
      nodes: wf.nodes,
      connections: wf.connections,
      settings: wf.settings
    };
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${wf.name.replace(/[^a-zA-Z0-9_-]/g, '_')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private onImportFromFile(): void {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const data = JSON.parse(reader.result as string);
          this.applyImportedData(data);
        } catch {
          console.error('Invalid JSON file');
        }
      };
      reader.readAsText(file);
    };
    input.click();
  }

  onImportedFromUri(data: any): void {
    this.showImportUriModal = false;
    this.applyImportedData(data);
  }

  private applyImportedData(data: any): void {
    if (!data) return;
    // Accept both flat format {nodes, connections} and wrapped format
    const nodes = data.nodes || [];
    const connections = data.connections || {};
    const settings = data.settings;
    this.store.importWorkflowData({ nodes, connections, settings });
  }

  private onPushToGit(): void {
    // Placeholder - source control integration
    alert('Push to git is not yet configured. Set up source control in Settings to enable this feature.');
  }

  private onOpenSettings(): void {
    // Load available workflows for the "Error Workflow" dropdown
    this.workflowService.list().subscribe({
      next: (workflows) => {
        const currentId = this.store.workflow()?.id;
        this.availableWorkflows = workflows
          .filter((w: Workflow) => w.id !== currentId)
          .map((w: Workflow) => ({ id: w.id!, name: w.name }));
        this.showSettingsModal = true;
      },
      error: () => {
        this.availableWorkflows = [];
        this.showSettingsModal = true;
      }
    });
  }

  onSettingsSaved(settings: WorkflowSettings): void {
    this.store.updateWorkflowSettings(settings);
    this.showSettingsModal = false;
  }

  private onUnpublish(): void {
    const wf = this.store.workflow();
    if (!wf?.id) return;
    this.workflowService.unpublish(wf.id).subscribe({
      next: (updated) => {
        this.store.workflow.set(updated);
      }
    });
  }

  onArchiveConfirmed(): void {
    this.showArchiveConfirm = false;
    const wf = this.store.workflow();
    if (!wf?.id) return;
    this.workflowService.archive(wf.id).subscribe({
      next: () => {
        this.router.navigate(['/home/workflows']);
      }
    });
  }
}
