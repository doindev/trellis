import { Component, HostListener, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Subscription } from 'rxjs';
import { WorkflowService } from '../../core/services';
import { WorkflowEditorStore } from '../../core/state/workflow-editor.store';
import { NodeTypeStore } from '../../core/state/node-type.store';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { NodePaletteComponent } from './components/node-palette/node-palette.component';
import { ReactFlowWrapperComponent } from './components/canvas/react-flow-wrapper.component';
import { ParameterPanelComponent } from './components/parameter-panel/parameter-panel.component';
import { EditorDrawerComponent } from './components/editor-drawer/editor-drawer.component';
import { NodeTypeDescription } from '../../core/models';

@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [
    CommonModule,
    ToolbarComponent,
    NodePaletteComponent,
    ReactFlowWrapperComponent,
    ParameterPanelComponent,
    EditorDrawerComponent
  ],
  templateUrl: './workflow-editor.component.html',
  styleUrl: './workflow-editor.component.scss'
})
export class WorkflowEditorComponent implements OnInit, OnDestroy {
  @ViewChild(ReactFlowWrapperComponent) canvasWrapper!: ReactFlowWrapperComponent;
  @ViewChild(NodePaletteComponent) nodePalette!: NodePaletteComponent;

  showPalette = false;
  drawerExpanded = false;
  activeTab: 'editor' | 'executions' = 'editor';
  pendingConnection: { sourceNodeId: string; sourceHandleId: string } | null = null;
  private executionSub?: Subscription;
  private autoSaveInterval: ReturnType<typeof setInterval> | null = null;
  private readonly AUTO_SAVE_INTERVAL = 3_000;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private workflowService: WorkflowService,
    public store: WorkflowEditorStore,
    public nodeTypeStore: NodeTypeStore
  ) {}

  ngOnInit(): void {
    this.nodeTypeStore.loadNodeTypes();

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.workflowService.get(id).subscribe({
        next: (workflow) => this.store.loadWorkflow(workflow),
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
    // Single-click only closes the panel (pane click); double-click opens it
    if (nodeId === null) {
      this.store.selectNode(null);
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
    this.drawerExpanded = true;
    this.executionSub?.unsubscribe();
    this.executionSub = this.workflowService.run(wf.id).subscribe({
      next: (result) => {
        this.store.setExecutionData(result);
        this.store.setIsExecuting(false);
      },
      error: () => {
        this.store.setIsExecuting(false);
      }
    });
  }

  onStopExecution(): void {
    this.executionSub?.unsubscribe();
    this.store.setIsExecuting(false);
  }

  ngOnDestroy(): void {
    this.executionSub?.unsubscribe();
    this.stopAutoSaveTimer();
  }

  onTabChanged(tab: 'editor' | 'executions'): void {
    this.activeTab = tab;
    if (tab === 'executions') {
      this.drawerExpanded = true;
    }
  }

  goBack(): void {
    this.router.navigate(['/home/workflows']);
  }
}
