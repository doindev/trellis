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

  @Output() nodeSelected = new EventEmitter<string | null>();
  @Output() nodeAdded = new EventEmitter<WorkflowNode>();
  @Output() nodeRemoved = new EventEmitter<string>();
  @Output() nodesPositionChanged = new EventEmitter<Record<string, [number, number]>>();
  @Output() connectionsChanged = new EventEmitter<Record<string, any>>();

  private root: Root | null = null;
  private nodeIdCounter = 0;

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
      onNodeClick: (nodeId: string) => {
        this.ngZone.run(() => this.nodeSelected.emit(nodeId));
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
      onNodeDelete: (nodeId: string) => {
        this.ngZone.run(() => this.nodeRemoved.emit(nodeId));
      },
      onNodesChange: (positions: Record<string, [number, number]>) => {
        this.ngZone.run(() => this.nodesPositionChanged.emit(positions));
      },
      onConnectionsChange: (connections: any) => {
        this.ngZone.run(() => this.connectionsChanged.emit(connections));
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
}
