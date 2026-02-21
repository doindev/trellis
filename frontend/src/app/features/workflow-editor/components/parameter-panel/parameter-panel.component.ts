import { Component, Input, Output, EventEmitter, HostListener, OnInit, OnDestroy, Pipe, PipeTransform, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { WorkflowNode, NodeTypeDescription, NodeParameter } from '../../../../core/models';
import { SettingsService, WebhookTestService, WebSocketService, WorkflowService } from '../../../../core/services';
import { StringParamComponent } from './parameter-renderers/string-param.component';
import { NumberParamComponent } from './parameter-renderers/number-param.component';
import { BooleanParamComponent } from './parameter-renderers/boolean-param.component';
import { OptionsParamComponent } from './parameter-renderers/options-param.component';
import { MultiOptionsParamComponent } from './parameter-renderers/multi-options-param.component';
import { JsonParamComponent } from './parameter-renderers/json-param.component';
import { CollectionParamComponent } from './parameter-renderers/collection-param.component';
import { FixedCollectionParamComponent } from './parameter-renderers/fixed-collection-param.component';
import { NoticeParamComponent } from './parameter-renderers/notice-param.component';
import { CredentialParamComponent } from './parameter-renderers/credential-param.component';
import { ModelParamComponent } from './parameter-renderers/model-param.component';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  CheckCircle, Copy, Square, Search, ChevronRight,
} from 'lucide-angular';
import { NODE_ICON_SET } from '../../../../shared/node-icons';

/** Schema node for the Schema tree view */
export interface SchemaNode {
  key: string;
  type: string;         // 'string' | 'number' | 'boolean' | 'object' | 'array' | 'null'
  value: string;        // display value (sample)
  path: string;
  level: number;
  children?: SchemaNode[];
  expanded?: boolean;
}

/** Pipe that highlights search matches with <mark> tags */
@Pipe({ name: 'highlight', standalone: true })
export class HighlightPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}
  transform(text: string, search: string): SafeHtml {
    if (!search || !text) return text;
    const escaped = search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escaped})`, 'gi');
    const highlighted = text.replace(regex, '<mark class="search-highlight">$1</mark>');
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }
}

@Component({
  selector: 'app-parameter-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideAngularModule,
    HighlightPipe,
    StringParamComponent,
    NumberParamComponent,
    BooleanParamComponent,
    OptionsParamComponent,
    MultiOptionsParamComponent,
    JsonParamComponent,
    CollectionParamComponent,
    FixedCollectionParamComponent,
    NoticeParamComponent,
    CredentialParamComponent,
    ModelParamComponent,
  ],
  providers: [{
    provide: LUCIDE_ICONS,
    multi: true,
    useValue: new LucideIconProvider({
      ...NODE_ICON_SET, CheckCircle, Copy, Square, Search, ChevronRight
    })
  }],
  templateUrl: './parameter-panel.component.html',
  styleUrl: './parameter-panel.component.scss'
})
export class ParameterPanelComponent implements OnInit, OnDestroy {
  @Input() node!: WorkflowNode;
  @Input() nodeType?: NodeTypeDescription;
  @Input() workflowId = '';
  @Input() allNodes: WorkflowNode[] = [];
  @Input() connections: Record<string, any> = {};
  @Input() executionData: Record<string, any> | null = null;
  @Input() nodeTypeMap: Map<string, NodeTypeDescription> = new Map();
  @Input() readOnly = false;
  @Input() webhookTestData: Record<string, any> = {};
  @Output() parameterChanged = new EventEmitter<Record<string, any>>();
  @Output() credentialChanged = new EventEmitter<Record<string, any>>();
  @Output() webhookTestDataReceived = new EventEmitter<{ nodeId: string; data: any }>();
  @Output() close = new EventEmitter<void>();
  @Output() deleteNode = new EventEmitter<void>();
  @Output() navigateToNode = new EventEmitter<string>();
  @Output() nodeExecuted = new EventEmitter<{ nodeId: string; data: any }>();

  @ViewChild('inputSearchEl') inputSearchEl?: ElementRef<HTMLInputElement>;
  @ViewChild('outputSearchEl') outputSearchEl?: ElementRef<HTMLInputElement>;

  activeTab: 'parameters' | 'settings' = 'parameters';

  // Webhook URL state
  webhookUrlProduction = 'http://localhost:5678/webhook/';
  webhookUrlTest = 'http://localhost:5678/webhook-test/';
  webhookUrlsExpanded = true;
  webhookUrlMode: 'test' | 'production' = 'test';
  copiedField: string | null = null;
  private _localPath: string | null = null;

  // Display mode and search state
  inputDisplayMode: 'schema' | 'table' | 'json' = 'schema';
  outputDisplayMode: 'schema' | 'table' | 'json' = 'schema';
  inputSearch = '';
  outputSearch = '';
  inputSearchExpanded = false;
  outputSearchExpanded = false;
  inputSchemaCollapsed = new Set<string>();
  outputSchemaCollapsed = new Set<string>();

  // Node execution state
  isNodeExecuting = false;
  nodeExecutionError: string | null = null;

  // Webhook test listening state
  isListening = false;
  listeningTestUrl = '';
  private webhookTopic = '';

  /** Derived from parent-managed webhookTestData map — scoped to current node */
  get receivedTestData(): any {
    return this.webhookTestData?.[this.node?.id] ?? null;
  }

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

  constructor(
    private settingsService: SettingsService,
    private webhookTestService: WebhookTestService,
    private webSocketService: WebSocketService,
    private workflowService: WorkflowService,
  ) {}

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
    return JSON.stringify(this.extractItems(data), null, 2);
  }

  getNodeOutputJson(nodeId: string): string {
    const data = this.executionData?.[nodeId];
    if (!data) return '';
    return JSON.stringify(this.extractItems(data), null, 2);
  }

  get inputItemCount(): number {
    let count = 0;
    for (const prev of this.previousNodes) {
      const data = this.executionData?.[prev.node.id] ?? this.webhookTestData?.[prev.node.id];
      if (data) count += this.extractItems(data).length;
    }
    return count;
  }

  get outputItemCount(): number {
    const data = this.nodeExecutionData;
    if (!data) return 0;
    return this.extractItems(data).length;
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

  getNodeCredentialId(credType: string): string | null {
    return this.node.credentials?.[credType]?.id || null;
  }

  getSettingValue(name: string, defaultValue: any): any {
    // Settings are stored in node.parameters via the same parameterChanged flow
    return this.node.parameters?.[name] ?? (this.node as any)[name] ?? defaultValue;
  }

  // --- Display mode / table / schema / search helpers ---

  /**
   * Extracts flat row data from execution data.
   * Execution data shape: [[{json: {...}}, {json: {...}}]]
   */
  extractItems(rawData: any): Record<string, any>[] {
    if (!rawData) return [];
    let items: any[] = [];
    if (Array.isArray(rawData) && rawData.length > 0 && Array.isArray(rawData[0])) {
      items = rawData[0];
    } else if (Array.isArray(rawData)) {
      items = rawData;
    } else if (typeof rawData === 'object') {
      return [rawData];
    }
    return items.map((item: any) => item?.json ?? item);
  }

  extractHeaders(rows: Record<string, any>[]): string[] {
    const keys = new Set<string>();
    for (const row of rows) {
      if (row && typeof row === 'object') {
        Object.keys(row).forEach(k => keys.add(k));
      }
    }
    return Array.from(keys);
  }

  filterRows(rows: Record<string, any>[], search: string): Record<string, any>[] {
    if (!search) return rows;
    const term = search.toLowerCase();
    return rows.filter(row =>
      Object.entries(row).some(([k, v]) => {
        const str = typeof v === 'object' ? JSON.stringify(v) : String(v ?? '');
        return k.toLowerCase().includes(term) || str.toLowerCase().includes(term);
      })
    );
  }

  formatCell(value: any): string {
    if (value === null || value === undefined) return '';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }

  // --- Schema helpers ---

  /** Determine JS type as a display string */
  private typeOf(value: any): string {
    if (value === null) return 'null';
    if (Array.isArray(value)) return 'array';
    return typeof value;
  }

  /** Type abbreviation shown in schema pills */
  typeIcon(type: string): string {
    switch (type) {
      case 'string': return 'Aa';
      case 'number': return '#';
      case 'boolean': return '✓';
      case 'object': return '{}';
      case 'array': return '[]';
      case 'null': return '∅';
      default: return '?';
    }
  }

  /** Build a merged schema tree from a list of data items */
  buildSchema(items: Record<string, any>[], parentPath = '', level = 0): SchemaNode[] {
    // Merge all items into one superset object
    const merged: Record<string, any> = {};
    for (const item of items) {
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        for (const [k, v] of Object.entries(item)) {
          if (!(k in merged)) merged[k] = v;
        }
      }
    }
    return this.objectToSchema(merged, parentPath, level);
  }

  private objectToSchema(obj: Record<string, any>, parentPath: string, level: number): SchemaNode[] {
    const nodes: SchemaNode[] = [];
    for (const [key, value] of Object.entries(obj)) {
      const path = parentPath ? `${parentPath}.${key}` : key;
      const type = this.typeOf(value);
      const node: SchemaNode = { key, type, path, level, value: '', expanded: true };

      if (type === 'object' && value !== null) {
        node.children = this.objectToSchema(value, path, level + 1);
        node.value = `{${Object.keys(value).length}}`;
      } else if (type === 'array') {
        node.value = `[${(value as any[]).length}]`;
        if ((value as any[]).length > 0 && typeof value[0] === 'object' && value[0] !== null) {
          node.children = this.objectToSchema(value[0], path + '[0]', level + 1);
        }
      } else {
        node.value = value === null ? 'null' : String(value);
      }
      nodes.push(node);
    }
    return nodes;
  }

  /** Flatten a schema tree respecting collapsed state */
  flattenSchema(nodes: SchemaNode[], collapsed: Set<string>, search: string): SchemaNode[] {
    const result: SchemaNode[] = [];
    const term = search.toLowerCase();
    for (const node of nodes) {
      const matches = !search || node.key.toLowerCase().includes(term) || node.value.toLowerCase().includes(term);
      const hasChildren = node.children && node.children.length > 0;
      const childFlat = hasChildren ? this.flattenSchema(node.children!, collapsed, search) : [];
      const childrenMatch = childFlat.length > 0;

      if (matches || childrenMatch) {
        result.push(node);
        if (hasChildren) {
          const isCollapsed = collapsed.has(node.path) && !search;
          if (!isCollapsed) {
            result.push(...childFlat);
          }
        }
      }
    }
    return result;
  }

  toggleSchemaNode(path: string, panel: 'input' | 'output'): void {
    const set = panel === 'input' ? this.inputSchemaCollapsed : this.outputSchemaCollapsed;
    if (set.has(path)) { set.delete(path); } else { set.add(path); }
  }

  isSchemaCollapsed(path: string, panel: 'input' | 'output'): boolean {
    return (panel === 'input' ? this.inputSchemaCollapsed : this.outputSchemaCollapsed).has(path);
  }

  get inputSchemaNodes(): SchemaNode[] {
    const items: Record<string, any>[] = [];
    for (const prev of this.previousNodes) {
      const data = this.executionData?.[prev.node.id] ?? this.webhookTestData?.[prev.node.id];
      if (data) items.push(...this.extractItems(data));
    }
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.inputSchemaCollapsed, this.inputSearch);
  }

  get outputSchemaNodes(): SchemaNode[] {
    const data = this.receivedTestData ?? this.nodeExecutionData;
    if (!data) return [];
    const items = this.extractItems(data);
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.outputSchemaCollapsed, this.outputSearch);
  }

  // --- Table helpers ---

  get inputTableRows(): Record<string, any>[] {
    const allRows: Record<string, any>[] = [];
    for (const prev of this.previousNodes) {
      const data = this.executionData?.[prev.node.id] ?? this.webhookTestData?.[prev.node.id];
      if (data) allRows.push(...this.extractItems(data));
    }
    return this.filterRows(allRows, this.inputSearch);
  }

  get inputTableHeaders(): string[] {
    return this.extractHeaders(this.inputTableRows);
  }

  get outputTableRows(): Record<string, any>[] {
    const data = this.receivedTestData ?? this.nodeExecutionData;
    if (!data) return [];
    return this.filterRows(this.extractItems(data), this.outputSearch);
  }

  get outputTableHeaders(): string[] {
    return this.extractHeaders(this.outputTableRows);
  }

  // --- JSON helpers ---

  getFilteredInputJson(nodeId: string): string {
    const data = this.executionData?.[nodeId] ?? this.webhookTestData?.[nodeId];
    if (!data) return '';
    const items = this.extractItems(data);
    if (!this.inputSearch) return JSON.stringify(items, null, 2);
    const filtered = this.filterRows(items, this.inputSearch);
    return filtered.length > 0 ? JSON.stringify(filtered, null, 2) : '';
  }

  get filteredOutputJson(): string {
    const data = this.receivedTestData ?? this.nodeExecutionData;
    if (!data) return '';
    const items = this.extractItems(data);
    if (!this.outputSearch) return JSON.stringify(items, null, 2);
    const filtered = this.filterRows(items, this.outputSearch);
    return filtered.length > 0 ? JSON.stringify(filtered, null, 2) : '';
  }

  // --- Shared data helpers ---

  get hasInputData(): boolean {
    return this.previousNodes.some(prev =>
      !!this.executionData?.[prev.node.id] || !!this.webhookTestData?.[prev.node.id]
    );
  }

  get hasOutputData(): boolean {
    return !!(this.receivedTestData || this.nodeExecutionData);
  }

  onSearchBlur(panel: 'input' | 'output'): void {
    if (panel === 'input' && !this.inputSearch) {
      this.inputSearchExpanded = false;
    } else if (panel === 'output' && !this.outputSearch) {
      this.outputSearchExpanded = false;
    }
  }

  toggleSearch(panel: 'input' | 'output'): void {
    if (panel === 'input') {
      this.inputSearchExpanded = !this.inputSearchExpanded;
      if (this.inputSearchExpanded) {
        setTimeout(() => this.inputSearchEl?.nativeElement.focus());
      }
    } else {
      this.outputSearchExpanded = !this.outputSearchExpanded;
      if (this.outputSearchExpanded) {
        setTimeout(() => this.outputSearchEl?.nativeElement.focus());
      }
    }
  }

  // --- Webhook test listening ---

  startListening(): void {
    if (!this.workflowId || !this.node?.id) return;
    const method = this.currentHttpMethod;
    const path = this.webhookPath;
    this.webhookTestDataReceived.emit({ nodeId: this.node.id, data: null });

    this.webhookTestService.startListening(this.workflowId, this.node.id, method, path).subscribe({
      next: (res) => {
        this.isListening = true;
        // Build full test URL from settings base + path
        this.listeningTestUrl = this.fullWebhookTestUrl;
        this.subscribeToWebhookTopic();
      },
      error: (err) => {
        console.error('Failed to start listening:', err);
      }
    });
  }

  stopListening(): void {
    if (!this.workflowId) return;
    this.webhookTestService.stopListening(this.workflowId).subscribe({
      next: () => {
        this.isListening = false;
        this.unsubscribeFromWebhookTopic();
      },
      error: (err) => {
        console.error('Failed to stop listening:', err);
        this.isListening = false;
        this.unsubscribeFromWebhookTopic();
      }
    });
  }

  copyTestUrl(): void {
    navigator.clipboard.writeText(this.listeningTestUrl || this.fullWebhookTestUrl);
    this.copiedField = 'testUrl';
    setTimeout(() => this.copiedField = null, 2000);
  }

  get receivedTestDataJson(): string {
    if (!this.receivedTestData) return '';
    return JSON.stringify(this.receivedTestData, null, 2);
  }

  private subscribeToWebhookTopic(): void {
    this.webhookTopic = `/topic/webhook-test/${this.workflowId}`;
    this.webSocketService.subscribe(this.webhookTopic, (message) => {
      const event = JSON.parse(message.body);
      if (event.event === 'testData') {
        this.webhookTestDataReceived.emit({ nodeId: this.node.id, data: event.data });
        this.isListening = false;
        this.unsubscribeFromWebhookTopic();
      } else if (event.event === 'testWebhookTimeout' || event.event === 'testWebhookCancelled') {
        this.isListening = false;
        this.unsubscribeFromWebhookTopic();
      }
    });
  }

  private unsubscribeFromWebhookTopic(): void {
    if (this.webhookTopic) {
      this.webSocketService.unsubscribe(this.webhookTopic);
      this.webhookTopic = '';
    }
  }

  ngOnDestroy(): void {
    if (this.isListening && this.workflowId) {
      this.webhookTestService.stopListening(this.workflowId).subscribe();
    }
    this.unsubscribeFromWebhookTopic();
  }

  // --- Single node execution ---

  get isTriggerNode(): boolean {
    return this.nodeType?.isTrigger === true;
  }

  /** Collect input items from upstream nodes in {json: ...} format for the backend */
  private collectInputForExecution(): any[] {
    const items: any[] = [];
    for (const prev of this.previousNodes) {
      const data = this.executionData?.[prev.node.id] ?? this.webhookTestData?.[prev.node.id];
      if (data && Array.isArray(data) && data.length > 0 && Array.isArray(data[0])) {
        items.push(...data[0]);
      } else if (data && Array.isArray(data)) {
        items.push(...data);
      }
    }
    return items;
  }

  executeNode(): void {
    if (this.isNodeExecuting || !this.node) return;
    this.isNodeExecuting = true;
    this.nodeExecutionError = null;
    // Clear current output for this node
    this.nodeExecuted.emit({ nodeId: this.node.id, data: null });

    const inputData = this.collectInputForExecution();

    this.workflowService.executeNode({
      nodeType: this.node.type,
      typeVersion: this.node.typeVersion,
      parameters: this.node.parameters,
      credentials: this.node.credentials,
      inputData,
      workflowId: this.workflowId,
      nodeId: this.node.id,
    }).subscribe({
      next: (res) => {
        this.isNodeExecuting = false;
        if (res.error) {
          this.nodeExecutionError = res.error;
        }
        this.nodeExecuted.emit({ nodeId: this.node.id, data: res.output });
      },
      error: (err) => {
        this.isNodeExecuting = false;
        this.nodeExecutionError = err.message || 'Execution failed';
      }
    });
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
