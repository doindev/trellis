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

  private root: Root | null = null;
  private nodeIdCounter = 0;
  private viewportHelper: { getViewportCenter: () => { x: number; y: number } } | null = null;

  constructor(private ngZone: NgZone) {}

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
    };

    this.root.render(createElement(TrellisCanvas, props));
  }

  private buildReactNodes(): any[] {
    if (!this.workflow?.nodes) return [];

    return this.workflow.nodes.map((node) => {
      const typeDesc = this.nodeTypeMap.get(node.type);
      const isTrigger = typeDesc?.isTrigger || false;
      const execData = this.executionData?.[node.id];

      return {
        id: node.id,
        type: isTrigger ? 'trellisTriggerNode' : 'trellisNode',
        position: { x: node.position[0], y: node.position[1] },
        selected: node.id === this.selectedNodeId,
        data: {
          label: node.name,
          typeDescription: typeDesc ? {
            displayName: typeDesc.displayName,
            icon: typeDesc.icon,
            subtitle: typeDesc.subtitle,
            inputs: typeDesc.inputs,
            outputs: typeDesc.outputs,
            isTrigger: typeDesc.isTrigger,
          } : undefined,
          executionStatus: execData?.status,
          itemCount: execData?.itemCount,
          disabled: node.disabled,
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
      if (conn?.main) {
        conn.main.forEach((outputs: any[], outputIndex: number) => {
          if (outputs) {
            outputs.forEach((target: any, targetIdx: number) => {
              edges.push({
                id: `e-${sourceNodeId}-${outputIndex}-${target.node}-${targetIdx}`,
                source: sourceNodeId,
                target: target.node,
                sourceHandle: 'main',
                targetHandle: 'main',
                type: 'trellisEdge',
                data: {
                  animated: this.executionData != null,
                },
              });
            });
          }
        });
      }
    });

    return edges;
  }

  /** Add a node at the center of the visible viewport, offset to avoid overlap. Returns the new node ID. */
  addNodeAtViewportCenter(type: string, displayName: string, version: number): string {
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
      parameters: {},
      position: [x, y],
    };
    this.nodeAdded.emit(newNode);
    return id;
  }
}
