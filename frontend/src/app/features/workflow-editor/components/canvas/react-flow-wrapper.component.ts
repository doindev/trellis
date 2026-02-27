import {
  Component,
  Input,
  Output,
  EventEmitter,
  ElementRef,
  ViewChild,
  OnChanges,
  OnDestroy,
  AfterViewInit,
  SimpleChanges,
  NgZone,
  ViewEncapsulation,
} from '@angular/core';
import { createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import TrellisCanvas, { type TrellisCanvasProps } from './trellis-canvas';
import { Workflow, WorkflowNode, NodeTypeDescription } from '../../../../core/models';

@Component({
  selector: 'app-react-flow-wrapper',
  standalone: true,
  template: `<div #reactFlowHost class="react-flow-host"></div>`,
  styleUrl: './canvas-styles.scss',
  encapsulation: ViewEncapsulation.None,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
    .react-flow-host {
      width: 100%;
      height: 100%;
    }
  `]
})
export class ReactFlowWrapperComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('reactFlowHost', { static: true }) hostRef!: ElementRef<HTMLDivElement>;

  @Input() workflow: Workflow | null = null;
  @Input() nodeTypeMap: Map<string, NodeTypeDescription> = new Map();
  @Input() executionData: Record<string, any> | null = null;
  @Input() selectedNodeId: string | null = null;
  @Input() isExecuting = false;
  @Input() readOnly = false;
  @Input() drawerOffset = 0;

  @Output() nodeSelected = new EventEmitter<string | null>();
  @Output() nodeDoubleClicked = new EventEmitter<string>();
  @Output() execute = new EventEmitter<void>();
  @Output() stopExecution = new EventEmitter<void>();
  @Output() nodeAdded = new EventEmitter<WorkflowNode>();
  @Output() nodeRemoved = new EventEmitter<string>();
  @Output() nodesPositionChanged = new EventEmitter<Record<string, [number, number]>>();
  @Output() connectionsChanged = new EventEmitter<Record<string, any>>();
  @Output() outputHandleDoubleClicked = new EventEmitter<{ nodeId: string; handleId: string }>();
  @Output() toggleNodeDisabled = new EventEmitter<string>();
  @Output() duplicateNode = new EventEmitter<string>();
  @Output() executeFromNode = new EventEmitter<string>();
  @Output() copyNode = new EventEmitter<string>();
  @Output() insertNodeOnEdge = new EventEmitter<{ sourceNodeId: string; targetNodeId: string; sourceHandle: string; targetHandle: string }>();

  private root: Root | null = null;
  private nodeIdCounter = 0;
  private viewportHelper: { getViewportCenter: () => { x: number; y: number } } | null = null;

  // Stable callback references — created once, never recreated
  private callbacks: Partial<TrellisCanvasProps> | null = null;

  constructor(private ngZone: NgZone) {}

  private getCallbacks(): Partial<TrellisCanvasProps> {
    if (!this.callbacks) {
      this.callbacks = {
        onNodeClick: (nodeId: string) => {
          this.ngZone.run(() => this.nodeSelected.emit(nodeId));
        },
        onNodeDoubleClick: (nodeId: string) => {
          this.ngZone.run(() => this.nodeDoubleClicked.emit(nodeId));
        },
        onNodeAdd: (type: string, position: { x: number; y: number }, displayName: string, version: number) => {
          this.ngZone.run(() => {
            const id = `node_${Date.now()}_${this.nodeIdCounter++}`;
            const newNode: WorkflowNode = {
              id,
              name: displayName,
              type,
              typeVersion: version,
              parameters: {},
              position: [position.x, position.y],
            };
            this.nodeAdded.emit(newNode);
          });
        },
        onPaneClick: () => {
          this.ngZone.run(() => this.nodeSelected.emit(null));
        },
        onNodeDelete: (nodeId: string) => {
          this.ngZone.run(() => this.nodeRemoved.emit(nodeId));
        },
        onNodesChange: (positions: Record<string, [number, number]>) => {
          this.ngZone.run(() => this.nodesPositionChanged.emit(positions));
        },
        onConnectionsChange: (connections: any) => {
          this.ngZone.run(() => this.connectionsChanged.emit(connections));
        },
        onExecute: () => {
          this.ngZone.run(() => this.execute.emit());
        },
        onStopExecution: () => {
          this.ngZone.run(() => this.stopExecution.emit());
        },
        onViewportHelperReady: (helper) => {
          this.viewportHelper = helper;
        },
        onOutputHandleDoubleClick: (nodeId: string, handleId: string) => {
          this.ngZone.run(() => this.outputHandleDoubleClicked.emit({ nodeId, handleId }));
        },
        onToggleNodeDisabled: (nodeId: string) => {
          this.ngZone.run(() => this.toggleNodeDisabled.emit(nodeId));
        },
        onDuplicateNode: (nodeId: string) => {
          this.ngZone.run(() => this.duplicateNode.emit(nodeId));
        },
        onExecuteFromNode: (nodeId: string) => {
          this.ngZone.run(() => this.executeFromNode.emit(nodeId));
        },
        onCopyNode: (nodeId: string) => {
          this.ngZone.run(() => this.copyNode.emit(nodeId));
        },
        onInsertNodeOnEdge: (info: { sourceNodeId: string; targetNodeId: string; sourceHandle: string; targetHandle: string }) => {
          this.ngZone.run(() => this.insertNodeOnEdge.emit(info));
        },
      };
    }
    return this.callbacks;
  }

  ngAfterViewInit(): void {
    this.root = createRoot(this.hostRef.nativeElement);
    this.renderReact();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.root) {
      this.renderReact();
    }
  }

  ngOnDestroy(): void {
    if (this.root) {
      this.root.unmount();
      this.root = null;
    }
    this.callbacks = null;
  }

  private renderReact(): void {
    if (!this.root) return;

    const reactNodes = this.buildReactNodes();
    const reactEdges = this.buildReactEdges();

    const props: TrellisCanvasProps = {
      initialNodes: reactNodes,
      initialEdges: reactEdges,
      isExecuting: this.isExecuting,
      readOnly: this.readOnly,
      drawerOffset: this.drawerOffset,
      ...this.getCallbacks(),
    };

    this.root.render(createElement(TrellisCanvas, props));
  }

  private buildReactNodes(): any[] {
    if (!this.workflow?.nodes) return [];

    return this.workflow.nodes.map((node) => {
      const typeDesc = this.nodeTypeMap.get(node.type);
      const isTrigger = typeDesc?.isTrigger || false;
      const execData = this.executionData?.[node.id];
      // execData is an array like [{ status, data, ... }] from runData
      const execEntry = Array.isArray(execData) ? execData[0] : execData;

      return {
        id: node.id,
        type: isTrigger ? 'trellisTriggerNode' : 'trellisNode',
        position: { x: node.position[0], y: node.position[1] },
        selected: node.id === this.selectedNodeId,
        data: {
          label: node.name,
          nodeType: node.type,
          nodeParameters: node.parameters,
          typeDescription: typeDesc ? {
            displayName: typeDesc.displayName,
            icon: typeDesc.icon,
            subtitle: typeDesc.subtitle,
            inputs: this.computeNodeInputs(node, typeDesc),
            outputs: this.computeNodeOutputs(node, typeDesc),
            isTrigger: typeDesc.isTrigger,
          } : undefined,
          executionStatus: execEntry?.status,
          itemCount: execEntry?.itemCount,
          disabled: node.disabled,
          isPinned: !!(this.workflow?.pinData?.[node.id]),
          readOnly: this.readOnly,
        },
      };
    });
  }

  private buildReactEdges(): any[] {
    if (!this.workflow?.connections) return [];

    const edges: any[] = [];
    const connections = this.workflow.connections;

    Object.entries(connections).forEach(([sourceNodeId, conn]: [string, any]) => {
      if (!conn) return;
      // Iterate ALL connection type keys (main, ai_languageModel, ai_tool, etc.)
      Object.entries(conn).forEach(([connectionType, outputList]: [string, any]) => {
        if (!Array.isArray(outputList)) return;
        outputList.forEach((targets: any[], outputIndex: number) => {
          if (!targets) return;
          targets.forEach((target: any, targetIdx: number) => {
            const isAi = connectionType.startsWith('ai_');
            const sourceExec = this.getNodeExecStatus(sourceNodeId);
            const targetExec = this.getNodeExecStatus(target.node);

            // Determine the real output index for this edge.
            // For "main" connections the array position IS the output index.
            // For named types ("loop", "done", etc.) look up the index in the node type description.
            let realOutputIndex = outputIndex;
            if (connectionType !== 'main') {
              const sourceNode = this.workflow?.nodes?.find(n => n.id === sourceNodeId);
              if (sourceNode) {
                const typeDesc = this.nodeTypeMap.get(sourceNode.type);
                if (typeDesc?.outputs) {
                  const idx = typeDesc.outputs.findIndex((o: any) => o.name === connectionType);
                  if (idx >= 0) realOutputIndex = idx;
                }
              }
            }

            // Check if this specific output is active (has data) on the source node
            const sourceExecData = this.executionData?.[sourceNodeId];
            const sourceExecEntry = Array.isArray(sourceExecData) ? sourceExecData[0] : sourceExecData;
            const sourceActiveOutputs: number[] | undefined = sourceExecEntry?.activeOutputs;
            const isActiveOutput = !sourceActiveOutputs || sourceActiveOutputs.length === 0
              || sourceActiveOutputs.includes(realOutputIndex);

            // Animate edge only when data is actively flowing through THIS output
            const animated = this.isExecuting
              && isActiveOutput
              && sourceExec !== null && sourceExec !== 'running'
              && (targetExec === null || targetExec === 'running');

            // Show status color when both nodes have completed
            let edgeStatus: string | undefined;
            if (sourceExec && sourceExec !== 'running' && targetExec && targetExec !== 'running') {
              edgeStatus = (targetExec === 'error' || sourceExec === 'error') ? 'error' : 'success';
            }

            edges.push({
              id: `e-${sourceNodeId}-${connectionType}-${outputIndex}-${target.node}-${targetIdx}`,
              source: sourceNodeId,
              target: target.node,
              sourceHandle: `${connectionType}:${outputIndex}`,
              targetHandle: `${connectionType}:${target.index || 0}`,
              type: 'trellisEdge',
              data: {
                animated,
                status: edgeStatus,
                connectionType,
                isAi,
              },
            });
          });
        });
      });
    });

    return edges;
  }

  /** Get execution status for a node, or null if not yet executed */
  private getNodeExecStatus(nodeId: string): string | null {
    const execData = this.executionData?.[nodeId];
    if (!execData) return null;
    const entry = Array.isArray(execData) ? execData[0] : execData;
    return entry?.status || null;
  }

  /**
   * Compute the actual inputs for a node instance. Most nodes use the static type description,
   * but the Merge node has a dynamic number of inputs based on the numberInputs parameter.
   */
  private computeNodeInputs(node: WorkflowNode, typeDesc: NodeTypeDescription): any[] {
    if (node.type === 'merge') {
      const mode = node.parameters?.['mode'] || 'append';
      if (mode === 'append') {
        const numInputs = Math.max(2, Math.min(10, Number(node.parameters?.['numberInputs']) || 2));
        return Array.from({ length: numInputs }, (_, i) => ({
          name: `input${i + 1}`,
          displayName: `Input ${i + 1}`,
          type: 'main',
        }));
      }
    }
    return typeDesc.inputs;
  }

  /**
   * Compute the actual outputs for a node instance. Most nodes use the static type description,
   * but nodes like Switch have dynamic outputs based on their parameters.
   */
  private computeNodeOutputs(node: WorkflowNode, typeDesc: NodeTypeDescription): any[] {
    if (node.type === 'switch') {
      const mode = node.parameters?.['mode'] || 'rules';
      if (mode === 'rules') {
        const rules = (node.parameters?.['rules'] as any[]) || [];
        const outputs = rules.map((rule: any, i: number) => ({
          name: `output${i}`,
          displayName: rule?.outputLabel || `Output ${i}`,
          type: 'main',
        }));
        // Always show at least 1 output
        if (outputs.length === 0) {
          outputs.push({ name: 'output0', displayName: 'Output 0', type: 'main' });
        }
        // Add fallback output if configured
        if (node.parameters?.['fallbackOutput'] === 'extra') {
          outputs.push({ name: 'fallback', displayName: 'Fallback', type: 'main' });
        }
        return outputs;
      } else {
        // Expression mode: fixed number of outputs
        const numOutputs = Math.max(2, Math.min(10, (node.parameters?.['numberOutputs'] as number) || 4));
        return Array.from({ length: numOutputs }, (_, i) => ({
          name: `output${i}`,
          displayName: `Output ${i}`,
          type: 'main',
        }));
      }
    }
    return typeDesc.outputs;
  }

  /** Add a node at the center of the visible viewport, offset to avoid overlap. Returns the new node ID. */
  addNodeAtViewportCenter(type: string, displayName: string, version: number, initialParams?: Record<string, any>): string {
    const NODE_W = 150;
    const NODE_H = 50;
    const OFFSET_STEP = 60;

    let center = { x: 0, y: 0 };
    if (this.viewportHelper) {
      center = this.viewportHelper.getViewportCenter();
    }

    // Offset so the node is centered on that point
    let x = center.x - NODE_W / 2;
    let y = center.y - NODE_H / 2;

    // Nudge away from existing nodes
    const existingPositions = (this.workflow?.nodes || []).map(n => n.position);
    let attempts = 0;
    while (attempts < 20 && existingPositions.some(p =>
      Math.abs(p[0] - x) < NODE_W && Math.abs(p[1] - y) < NODE_H
    )) {
      x += OFFSET_STEP;
      y += OFFSET_STEP;
      attempts++;
    }

    const id = `node_${Date.now()}_${this.nodeIdCounter++}`;
    const newNode: WorkflowNode = {
      id,
      name: displayName,
      type,
      typeVersion: version,
      parameters: initialParams || {},
      position: [x, y],
    };
    this.nodeAdded.emit(newNode);
    return id;
  }
}
