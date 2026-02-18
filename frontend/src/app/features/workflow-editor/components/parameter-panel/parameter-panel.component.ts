import { Component, Input, Output, EventEmitter, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WorkflowNode, NodeTypeDescription, NodeParameter } from '../../../../core/models';
import { SettingsService } from '../../../../core/services';
import { StringParamComponent } from './parameter-renderers/string-param.component';
import { NumberParamComponent } from './parameter-renderers/number-param.component';
import { BooleanParamComponent } from './parameter-renderers/boolean-param.component';
import { OptionsParamComponent } from './parameter-renderers/options-param.component';
import { MultiOptionsParamComponent } from './parameter-renderers/multi-options-param.component';
import { JsonParamComponent } from './parameter-renderers/json-param.component';
import { CollectionParamComponent } from './parameter-renderers/collection-param.component';
import { FixedCollectionParamComponent } from './parameter-renderers/fixed-collection-param.component';
import { NoticeParamComponent } from './parameter-renderers/notice-param.component';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
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
    MultiOptionsParamComponent,
    JsonParamComponent,
    CollectionParamComponent,
    FixedCollectionParamComponent,
    NoticeParamComponent,
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
export class ParameterPanelComponent implements OnInit {
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

  // Webhook URL state
  webhookUrlProduction = 'http://localhost:5678/webhook/';
  webhookUrlTest = 'http://localhost:5678/webhook-test/';
  webhookUrlsExpanded = true;
  webhookUrlMode: 'test' | 'production' = 'test';
  copiedField: string | null = null;
  private _localPath: string | null = null;

  // Column resize state
  leftWidthPercent = 100 / 3;
  centerWidthPercent = 100 / 3;
  rightWidthPercent = 100 / 3;
  private readonly MIN_COL_PCT = 15;

  private dragType: 'left-border' | 'right-border' | 'center-handle' | null = null;
  private dragStartX = 0;
  private dragStartLeft = 0;
  private dragStartCenter = 0;
  private dragStartRight = 0;

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    if (this.isWebhookNode) {
      this.settingsService.getSettings().subscribe(settings => {
        this.webhookUrlProduction = settings.webhookUrlProduction || '';
        this.webhookUrlTest = settings.webhookUrlTest || '';
      });
    }
  }

  get isWebhookNode(): boolean {
    return this.node?.type === 'webhook';
  }

  get webhookPath(): string {
    const path = this._localPath ?? this.node?.parameters?.['path'] ?? '';
    return path.startsWith('/') ? path.substring(1) : path;
  }

  get fullWebhookUrl(): string {
    return this.webhookUrlProduction + this.webhookPath;
  }

  get fullWebhookTestUrl(): string {
    return this.webhookUrlTest + this.webhookPath;
  }

  get currentHttpMethod(): string {
    return this.node?.parameters?.['httpMethod'] || 'GET';
  }

  copyWebhookUrl(): void {
    const url = this.webhookUrlMode === 'test' ? this.fullWebhookTestUrl : this.fullWebhookUrl;
    navigator.clipboard.writeText(url);
    this.copiedField = 'url';
    setTimeout(() => this.copiedField = null, 2000);
  }

  get parameters(): NodeParameter[] {
    return this.nodeType?.parameters || [];
  }

  get visibleParameters(): NodeParameter[] {
    return this.parameters.filter(p => !p.isNodeSetting && this.isVisible(p));
  }

  get settingsParameters(): NodeParameter[] {
    return this.parameters.filter(p => p.isNodeSetting && this.isVisible(p));
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
    if (name === 'path' && this.isWebhookNode) {
      this._localPath = value;
    }
    const updated = { ...this.node.parameters, [name]: value };
    this.parameterChanged.emit(updated);
  }

  onParamBlur(name: string): void {
    if (name === 'path' && this.isWebhookNode) {
      const current = this._localPath ?? this.node?.parameters?.['path'] ?? '';
      if (current && !current.startsWith('/')) {
        const normalized = '/' + current;
        this._localPath = normalized;
        this.onParameterChange('path', normalized);
      }
    }
  }

  getParameterValue(name: string, defaultValue: any): any {
    return this.node.parameters[name] ?? defaultValue;
  }

  getSettingValue(name: string, defaultValue: any): any {
    // Settings are stored in node.parameters via the same parameterChanged flow
    return this.node.parameters?.[name] ?? (this.node as any)[name] ?? defaultValue;
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
