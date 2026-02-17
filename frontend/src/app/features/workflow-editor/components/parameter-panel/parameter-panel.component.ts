import { Component, Input, Output, EventEmitter, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WorkflowNode, NodeTypeDescription, NodeParameter } from '../../../../core/models';
import { StringParamComponent } from './parameter-renderers/string-param.component';
import { NumberParamComponent } from './parameter-renderers/number-param.component';
import { BooleanParamComponent } from './parameter-renderers/boolean-param.component';
import { OptionsParamComponent } from './parameter-renderers/options-param.component';
import { JsonParamComponent } from './parameter-renderers/json-param.component';
import { CollectionParamComponent } from './parameter-renderers/collection-param.component';
import { FixedCollectionParamComponent } from './parameter-renderers/fixed-collection-param.component';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code
} from 'lucide-angular';

@Component({
  selector: 'app-parameter-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideAngularModule,
    StringParamComponent,
    NumberParamComponent,
    BooleanParamComponent,
    OptionsParamComponent,
    JsonParamComponent,
    CollectionParamComponent,
    FixedCollectionParamComponent,
  ],
  providers: [{
    provide: LUCIDE_ICONS,
    multi: true,
    useValue: new LucideIconProvider({
      Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
      UnfoldVertical, Route, Pen, Code
    })
  }],
  templateUrl: './parameter-panel.component.html',
  styleUrl: './parameter-panel.component.scss'
})
export class ParameterPanelComponent {
  @Input() node!: WorkflowNode;
  @Input() nodeType?: NodeTypeDescription;
  @Input() allNodes: WorkflowNode[] = [];
  @Input() connections: Record<string, any> = {};
  @Input() executionData: Record<string, any> | null = null;
  @Input() nodeTypeMap: Map<string, NodeTypeDescription> = new Map();
  @Input() readOnly = false;
  @Output() parameterChanged = new EventEmitter<Record<string, any>>();
  @Output() close = new EventEmitter<void>();
  @Output() deleteNode = new EventEmitter<void>();
  @Output() navigateToNode = new EventEmitter<string>();

  activeTab: 'parameters' | 'settings' = 'parameters';

  // Column resize state
  leftWidthPercent = 25;
  centerWidthPercent = 50;
  rightWidthPercent = 25;
  private readonly MIN_COL_PCT = 15;

  private dragType: 'left-border' | 'right-border' | 'center-handle' | null = null;
  private dragStartX = 0;
  private dragStartLeft = 0;
  private dragStartCenter = 0;
  private dragStartRight = 0;

  get parameters(): NodeParameter[] {
    return this.nodeType?.parameters || [];
  }

  get visibleParameters(): NodeParameter[] {
    return this.parameters.filter(p => this.isVisible(p));
  }

  /** Nodes that connect INTO this node */
  get previousNodes(): { node: WorkflowNode; nodeType?: NodeTypeDescription }[] {
    const results: { node: WorkflowNode; nodeType?: NodeTypeDescription }[] = [];
    const seen = new Set<string>();
    for (const [sourceId, conn] of Object.entries(this.connections)) {
      if (!conn?.main) continue;
      for (const outputs of conn.main) {
        if (!outputs) continue;
        for (const c of outputs) {
          if (c.node === this.node.id && !seen.has(sourceId)) {
            seen.add(sourceId);
            const n = this.allNodes.find(nd => nd.id === sourceId);
            if (n) {
              results.push({ node: n, nodeType: this.nodeTypeMap.get(n.type) });
            }
          }
        }
      }
    }
    return results;
  }

  /** Nodes that this node connects TO */
  get nextNodes(): { node: WorkflowNode; nodeType?: NodeTypeDescription }[] {
    const conn = this.connections[this.node.id];
    if (!conn?.main) return [];
    const results: { node: WorkflowNode; nodeType?: NodeTypeDescription }[] = [];
    const seen = new Set<string>();
    for (const outputs of conn.main) {
      if (!outputs) continue;
      for (const c of outputs) {
        if (!seen.has(c.node)) {
          seen.add(c.node);
          const n = this.allNodes.find(nd => nd.id === c.node);
          if (n) {
            results.push({ node: n, nodeType: this.nodeTypeMap.get(n.type) });
          }
        }
      }
    }
    return results;
  }

  get nodeExecutionData(): any {
    return this.executionData?.[this.node.id] ?? null;
  }

  get outputJson(): string {
    const data = this.nodeExecutionData;
    if (!data) return '';
    return JSON.stringify(data, null, 2);
  }

  getNodeOutputJson(nodeId: string): string {
    const data = this.executionData?.[nodeId];
    if (!data) return '';
    return JSON.stringify(data, null, 2);
  }

  get inputItemCount(): number {
    for (const prev of this.previousNodes) {
      const data = this.executionData?.[prev.node.id];
      if (Array.isArray(data)) return data.length;
    }
    return 0;
  }

  get outputItemCount(): number {
    const data = this.nodeExecutionData;
    if (Array.isArray(data)) return data.length;
    return 0;
  }

  isVisible(param: NodeParameter): boolean {
    if (!param.displayOptions) return true;
    const show = param.displayOptions.show;
    if (!show) return true;

    return Object.entries(show).every(([key, values]: [string, any]) => {
      const currentVal = this.node.parameters[key];
      if (Array.isArray(values)) {
        return values.includes(currentVal);
      }
      return currentVal === values;
    });
  }

  onParameterChange(name: string, value: any): void {
    const updated = { ...this.node.parameters, [name]: value };
    this.parameterChanged.emit(updated);
  }

  getParameterValue(name: string, defaultValue: any): any {
    return this.node.parameters[name] ?? defaultValue;
  }

  // --- Resize handlers ---

  onBorderDragStart(event: MouseEvent, type: 'left-border' | 'right-border'): void {
    event.preventDefault();
    this.dragType = type;
    this.dragStartX = event.clientX;
    this.dragStartLeft = this.leftWidthPercent;
    this.dragStartCenter = this.centerWidthPercent;
    this.dragStartRight = this.rightWidthPercent;
  }

  onCenterHandleDragStart(event: MouseEvent): void {
    event.preventDefault();
    this.dragType = 'center-handle';
    this.dragStartX = event.clientX;
    this.dragStartLeft = this.leftWidthPercent;
    this.dragStartCenter = this.centerWidthPercent;
    this.dragStartRight = this.rightWidthPercent;
  }

  @HostListener('document:mousemove', ['$event'])
  onDragMove(event: MouseEvent): void {
    if (!this.dragType) return;
    const modalBody = document.querySelector('.node-modal-body') as HTMLElement;
    if (!modalBody) return;
    const totalWidth = modalBody.clientWidth;
    const deltaPx = event.clientX - this.dragStartX;
    const deltaPct = (deltaPx / totalWidth) * 100;

    if (this.dragType === 'left-border') {
      let newLeft = this.dragStartLeft + deltaPct;
      let newCenter = this.dragStartCenter - deltaPct;
      newLeft = Math.max(this.MIN_COL_PCT, Math.min(newLeft, 100 - this.MIN_COL_PCT - this.rightWidthPercent));
      newCenter = Math.max(this.MIN_COL_PCT, Math.min(newCenter, 100 - this.MIN_COL_PCT - this.rightWidthPercent));
      // Recalculate to maintain sum
      newCenter = 100 - newLeft - this.rightWidthPercent;
      if (newCenter < this.MIN_COL_PCT) {
        newCenter = this.MIN_COL_PCT;
        newLeft = 100 - newCenter - this.rightWidthPercent;
      }
      this.leftWidthPercent = newLeft;
      this.centerWidthPercent = newCenter;
    } else if (this.dragType === 'right-border') {
      let newCenter = this.dragStartCenter + deltaPct;
      let newRight = this.dragStartRight - deltaPct;
      newRight = Math.max(this.MIN_COL_PCT, Math.min(newRight, 100 - this.MIN_COL_PCT - this.leftWidthPercent));
      newCenter = 100 - this.leftWidthPercent - newRight;
      if (newCenter < this.MIN_COL_PCT) {
        newCenter = this.MIN_COL_PCT;
        newRight = 100 - this.leftWidthPercent - newCenter;
      }
      this.centerWidthPercent = newCenter;
      this.rightWidthPercent = newRight;
    } else if (this.dragType === 'center-handle') {
      // Center width stays constant, redistribute left and right
      let newLeft = this.dragStartLeft + deltaPct;
      let newRight = this.dragStartRight - deltaPct;
      newLeft = Math.max(this.MIN_COL_PCT, Math.min(newLeft, 100 - this.centerWidthPercent - this.MIN_COL_PCT));
      newRight = 100 - this.centerWidthPercent - newLeft;
      if (newRight < this.MIN_COL_PCT) {
        newRight = this.MIN_COL_PCT;
        newLeft = 100 - this.centerWidthPercent - newRight;
      }
      this.leftWidthPercent = newLeft;
      this.rightWidthPercent = newRight;
    }
  }

  @HostListener('document:mouseup')
  onDragEnd(): void {
    this.dragType = null;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close.emit();
    }
  }
}
