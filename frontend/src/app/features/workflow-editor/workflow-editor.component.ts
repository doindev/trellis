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
import { NodePaletteComponent } from './components/node-palette/node-palette.component';
import { ReactFlowWrapperComponent } from './components/canvas/react-flow-wrapper.component';
import { ParameterPanelComponent } from './components/parameter-panel/parameter-panel.component';
import { EditorDrawerComponent } from './components/editor-drawer/editor-drawer.component';
import { PublishModalComponent } from './components/publish-modal/publish-modal.component';
import { DescriptionModalComponent } from './components/description-modal/description-modal.component';
import { ImportUriModalComponent } from './components/import-uri-modal/import-uri-modal.component';
import { SettingsModalComponent, WorkflowSettings } from './components/settings-modal/settings-modal.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
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
    ExecutionsSidebarComponent
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
  pendingConnection: { sourceNodeId: string; sourceHandleId: string } | null = null;
  selectedExecutionId: string | null = null;
  viewingExecution: Execution | null = null;
  executionWorkflow: Workflow | null = null;
  executionDataById: Record<string, any> | null = null;
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
      setTimeout(() => this.nodePalette?.focusSearch(), 260);
    } else {
      this.pendingConnection = null;
    }
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
  }

  onPaletteNodeClicked(nodeType: NodeTypeDescription): void {
    const newNodeId = this.canvasWrapper.addNodeAtViewportCenter(nodeType.type, nodeType.displayName, nodeType.version);
    if (this.pendingConnection) {
      this.addConnectionFromPending(newNodeId);
      this.pendingConnection = null;
    }
    this.showPalette = false;
  }

  onOutputHandleDoubleClicked(event: { nodeId: string; handleId: string }): void {
    this.pendingConnection = { sourceNodeId: event.nodeId, sourceHandleId: event.handleId };
    if (!this.showPalette) {
      this.showPalette = true;
      setTimeout(() => this.nodePalette?.focusSearch(), 260);
    }
  }

  private addConnectionFromPending(targetNodeId: string): void {
    if (!this.pendingConnection) return;
    const { sourceNodeId, sourceHandleId } = this.pendingConnection;

    // Find the output index for the source handle
    const sourceNode = this.store.nodes().find(n => n.id === sourceNodeId);
    if (!sourceNode) return;

    const sourceType = this.nodeTypeStore.getByType(sourceNode.type);
    const outputs = sourceType?.outputs || [{ name: 'main', type: 'main' }];
    const outputIndex = outputs.findIndex(o => o.name === sourceHandleId);
    if (outputIndex < 0) return;

    const connections = JSON.parse(JSON.stringify(this.store.connections()));
    if (!connections[sourceNodeId]) {
      connections[sourceNodeId] = { main: [] };
    }
    // Ensure the main array has enough entries for the output index
    while (connections[sourceNodeId].main.length <= outputIndex) {
      connections[sourceNodeId].main.push([]);
    }
    connections[sourceNodeId].main[outputIndex].push({
      node: targetNodeId,
      type: 'main',
      index: 0,
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
