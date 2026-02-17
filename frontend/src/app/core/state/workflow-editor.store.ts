import { Injectable, signal, computed } from '@angular/core';
import { Workflow, WorkflowNode } from '../models';
import { WorkflowService } from '../services';

@Injectable({ providedIn: 'root' })
export class WorkflowEditorStore {
  readonly workflow = signal<Workflow | null>(null);
  readonly selectedNodeId = signal<string | null>(null);
  readonly executionData = signal<Record<string, any> | null>(null);
  readonly isDirty = signal(false);
  readonly isExecuting = signal(false);

  readonly selectedNode = computed(() => {
    const wf = this.workflow();
    const nodeId = this.selectedNodeId();
    if (!wf || !nodeId) return null;
    return wf.nodes.find(n => n.id === nodeId) || null;
  });

  readonly nodes = computed(() => this.workflow()?.nodes || []);
  readonly connections = computed(() => this.workflow()?.connections || {});

  constructor(private workflowService: WorkflowService) {}

  loadWorkflow(workflow: Workflow): void {
    this.workflow.set(workflow);
    this.selectedNodeId.set(null);
    this.executionData.set(null);
    this.isDirty.set(false);
  }

  createNew(): void {
    this.workflow.set({
      name: 'New Workflow',
      active: false,
      nodes: [],
      connections: {}
    });
    this.selectedNodeId.set(null);
    this.executionData.set(null);
    this.isDirty.set(true);
  }

  saveWorkflow(): void {
    const wf = this.workflow();
    if (!wf) return;

    const operation = wf.id
      ? this.workflowService.update(wf.id, wf)
      : this.workflowService.create(wf);

    operation.subscribe({
      next: (saved) => {
        this.workflow.set(saved);
        this.isDirty.set(false);
      },
      error: (err) => console.error('Failed to save workflow:', err)
    });
  }

  addNode(node: WorkflowNode): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({
      ...wf,
      nodes: [...wf.nodes, node]
    });
    this.isDirty.set(true);
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

    this.workflow.set({
      ...wf,
      nodes: wf.nodes.filter(n => n.id !== nodeId),
      connections: updatedConnections
    });
    this.isDirty.set(true);
    if (this.selectedNodeId() === nodeId) {
      this.selectedNodeId.set(null);
    }
  }

  updateNode(nodeId: string, updates: Partial<WorkflowNode>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({
      ...wf,
      nodes: wf.nodes.map(n => n.id === nodeId ? { ...n, ...updates } : n)
    });
    this.isDirty.set(true);
  }

  selectNode(nodeId: string | null): void {
    this.selectedNodeId.set(nodeId);
  }

  updateConnections(connections: Record<string, any>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({ ...wf, connections });
    this.isDirty.set(true);
  }

  updateWorkflowName(name: string): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({ ...wf, name });
    this.isDirty.set(true);
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
    this.workflow.set({ ...wf, active: !wf.active });
    this.isDirty.set(true);
  }

  updateNodePositions(positions: Record<string, [number, number]>): void {
    const wf = this.workflow();
    if (!wf) return;
    this.workflow.set({
      ...wf,
      nodes: wf.nodes.map(n => positions[n.id] ? { ...n, position: positions[n.id] } : n)
    });
    this.isDirty.set(true);
  }
}
