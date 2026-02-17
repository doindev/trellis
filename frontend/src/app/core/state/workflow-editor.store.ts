import { Injectable, signal, computed } from '@angular/core';
import { Workflow, WorkflowNode } from '../models';
import { WorkflowService } from '../services';

interface WorkflowSnapshot {
  name: string;
  active: boolean;
  nodes: WorkflowNode[];
  connections: Record<string, any>;
  settings?: Record<string, any>;
}

@Injectable({ providedIn: 'root' })
export class WorkflowEditorStore {
  readonly workflow = signal<Workflow | null>(null);
  readonly selectedNodeId = signal<string | null>(null);
  readonly executionData = signal<Record<string, any> | null>(null);
  readonly isDirty = signal(false);
  readonly isExecuting = signal(false);
  readonly isSaving = signal(false);

  // Undo/redo history
  private history: WorkflowSnapshot[] = [];
  private _historyIndex = signal(-1);
  private _historyLength = signal(0);
  private isUndoRedoInProgress = false;
  private readonly MAX_HISTORY = 50;
  private lastChangeType = '';
  private lastChangeTime = 0;

  readonly canUndo = computed(() => this._historyIndex() > 0);
  readonly canRedo = computed(() => this._historyIndex() < this._historyLength() - 1);

  readonly selectedNode = computed(() => {
    const wf = this.workflow();
    const nodeId = this.selectedNodeId();
    if (!wf || !nodeId) return null;
    return wf.nodes.find(n => n.id === nodeId) || null;
  });

  readonly nodes = computed(() => this.workflow()?.nodes || []);
  readonly connections = computed(() => this.workflow()?.connections || {});

  constructor(private workflowService: WorkflowService) {}

  private takeSnapshot(wf: Workflow): WorkflowSnapshot {
    return {
      name: wf.name,
      active: wf.active,
      nodes: JSON.parse(JSON.stringify(wf.nodes)),
      connections: JSON.parse(JSON.stringify(wf.connections)),
      settings: wf.settings ? JSON.parse(JSON.stringify(wf.settings)) : undefined
    };
  }

  private pushSnapshot(wf: Workflow): void {
    const snapshot = this.takeSnapshot(wf);
    const idx = this._historyIndex();
    // Truncate any redo history
    this.history = this.history.slice(0, idx + 1);
    this.history.push(snapshot);
    // Enforce max size
    if (this.history.length > this.MAX_HISTORY) {
      this.history = this.history.slice(this.history.length - this.MAX_HISTORY);
    }
    this._historyIndex.set(this.history.length - 1);
    this._historyLength.set(this.history.length);
  }

  private resetHistory(wf: Workflow): void {
    this.history = [];
    this._historyIndex.set(-1);
    this._historyLength.set(0);
    this.pushSnapshot(wf);
    this.lastChangeType = '';
    this.lastChangeTime = 0;
  }

  private applySnapshot(snapshot: WorkflowSnapshot): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({
      ...wf,
      name: snapshot.name,
      active: snapshot.active,
      nodes: JSON.parse(JSON.stringify(snapshot.nodes)),
      connections: JSON.parse(JSON.stringify(snapshot.connections)),
      settings: snapshot.settings ? JSON.parse(JSON.stringify(snapshot.settings)) : wf.settings
    });
  }

  private commitChange(newWorkflow: Workflow, changeType?: string): void {
    this.workflow.set(newWorkflow);
    this.isDirty.set(true);
    if (!this.isUndoRedoInProgress) {
      const now = Date.now();
      // Collapse rapid position changes into a single history entry
      if (changeType === 'positions' && this.lastChangeType === 'positions' && now - this.lastChangeTime < 500) {
        // Replace the last snapshot instead of pushing
        const idx = this._historyIndex();
        if (idx >= 0) {
          this.history[idx] = this.takeSnapshot(newWorkflow);
        }
      } else {
        this.pushSnapshot(newWorkflow);
      }
      this.lastChangeType = changeType || '';
      this.lastChangeTime = now;
    }
  }

  undo(): void {
    if (!this.canUndo()) return;
    this.isUndoRedoInProgress = true;
    this._historyIndex.update(i => i - 1);
    this.applySnapshot(this.history[this._historyIndex()]);
    this.isDirty.set(true);
    this.isUndoRedoInProgress = false;
  }

  redo(): void {
    if (!this.canRedo()) return;
    this.isUndoRedoInProgress = true;
    this._historyIndex.update(i => i + 1);
    this.applySnapshot(this.history[this._historyIndex()]);
    this.isDirty.set(true);
    this.isUndoRedoInProgress = false;
  }

  loadWorkflow(workflow: Workflow): void {
    this.workflow.set(workflow);
    this.selectedNodeId.set(null);
    this.executionData.set(null);
    this.isDirty.set(false);
    this.resetHistory(workflow);
  }

  createNew(): void {
    const wf: Workflow = {
      name: 'New Workflow',
      active: false,
      nodes: [],
      connections: {}
    };
    this.workflow.set(wf);
    this.selectedNodeId.set(null);
    this.executionData.set(null);
    this.isDirty.set(true);
    this.resetHistory(wf);
  }

  saveWorkflow(): void {
    const wf = this.workflow();
    if (!wf || this.isSaving()) return;

    this.isSaving.set(true);
    const operation = wf.id
      ? this.workflowService.update(wf.id, wf)
      : this.workflowService.create(wf);

    operation.subscribe({
      next: (saved) => {
        this.workflow.set(saved);
        this.isDirty.set(false);
        this.isSaving.set(false);
      },
      error: (err) => {
        console.error('Failed to save workflow:', err);
        this.isSaving.set(false);
      }
    });
  }

  addNode(node: WorkflowNode): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({
      ...wf,
      nodes: [...wf.nodes, node]
    }, 'addNode');
  }

  removeNode(nodeId: string): void {
    const wf = this.workflow();
    if (!wf) return;

    const updatedConnections = { ...wf.connections };
    delete updatedConnections[nodeId];
    Object.keys(updatedConnections).forEach(key => {
      const conn = updatedConnections[key];
      if (conn?.main) {
        conn.main = conn.main.map((outputs: any[]) =>
          outputs ? outputs.filter((c: any) => c.node !== nodeId) : outputs
        );
      }
    });

    this.commitChange({
      ...wf,
      nodes: wf.nodes.filter(n => n.id !== nodeId),
      connections: updatedConnections
    }, 'removeNode');
    if (this.selectedNodeId() === nodeId) {
      this.selectedNodeId.set(null);
    }
  }

  updateNode(nodeId: string, updates: Partial<WorkflowNode>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({
      ...wf,
      nodes: wf.nodes.map(n => n.id === nodeId ? { ...n, ...updates } : n)
    }, 'updateNode');
  }

  selectNode(nodeId: string | null): void {
    this.selectedNodeId.set(nodeId);
  }

  updateConnections(connections: Record<string, any>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({ ...wf, connections }, 'connections');
  }

  updateWorkflowName(name: string): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({ ...wf, name }, 'name');
  }

  setExecutionData(data: Record<string, any> | null): void {
    this.executionData.set(data);
  }

  setIsExecuting(executing: boolean): void {
    this.isExecuting.set(executing);
  }

  toggleActive(): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({ ...wf, active: !wf.active }, 'toggleActive');
  }

  updateNodePositions(positions: Record<string, [number, number]>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.commitChange({
      ...wf,
      nodes: wf.nodes.map(n => positions[n.id] ? { ...n, position: positions[n.id] } : n)
    }, 'positions');
  }
}
