import { Component, Input, Output, EventEmitter, HostListener, OnInit, OnDestroy, OnChanges, SimpleChanges, Pipe, PipeTransform, ViewChild, ElementRef } from '@angular/core';
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
import { FixedCollectionParamComponent, FixedCollectionExpressionEvent } from './parameter-renderers/fixed-collection-param.component';
import { NoticeParamComponent } from './parameter-renderers/notice-param.component';
import { CredentialParamComponent } from './parameter-renderers/credential-param.component';
import { ModelParamComponent } from './parameter-renderers/model-param.component';
import { CacheNameParamComponent } from './parameter-renderers/cache-name-param.component';
import { ExpressionEditorModalComponent, ExpressionAncestorNode } from './expression-editor-modal.component';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  CheckCircle, Copy, Square, Search, ChevronRight, Pin, PinOff, Pencil,
} from 'lucide-angular';
import { NODE_ICON_SET } from '../../../../shared/node-icons';

/** A single line in the JSON tree view */
export interface JsonTreeLine {
  indent: number;
  type: 'bracket' | 'keyValue' | 'keyOpen' | 'value';
  key?: string;        // key name (for keyValue and keyOpen)
  path?: string;       // full dot-notation path for drag (e.g. 'headers.host')
  bracket?: string;    // bracket character(s) like '{', '},' '[', '],'
  displayValue?: string; // formatted value (for keyValue and value lines)
  valueType?: string;  // 'string' | 'number' | 'boolean' | 'null' | 'object' | 'array'
}

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
    CacheNameParamComponent,
    ExpressionEditorModalComponent,
  ],
  providers: [{
    provide: LUCIDE_ICONS,
    multi: true,
    useValue: new LucideIconProvider({
      ...NODE_ICON_SET, CheckCircle, Copy, Square, Search, ChevronRight, Pin, PinOff, Pencil
    })
  }],
  templateUrl: './parameter-panel.component.html',
  styleUrl: './parameter-panel.component.scss'
})
export class ParameterPanelComponent implements OnInit, OnDestroy, OnChanges {
  @Input() node!: WorkflowNode;
  @Input() nodeType?: NodeTypeDescription;
  @Input() workflowId = '';
  @Input() projectId = '';
  @Input() allNodes: WorkflowNode[] = [];
  @Input() connections: Record<string, any> = {};
  @Input() executionData: Record<string, any> | null = null;
  @Input() nodeTypeMap: Map<string, NodeTypeDescription> = new Map();
  @Input() readOnly = false;
  @Input() projectContextPath = '';
  @Input() webhookTestData: Record<string, any> = {};
  @Input() pinData: Record<string, any> = {};
  @Output() parameterChanged = new EventEmitter<Record<string, any>>();
  @Output() pinDataChanged = new EventEmitter<{ nodeId: string; items: any[] | null }>();
  @Output() credentialChanged = new EventEmitter<Record<string, any>>();
  @Output() webhookTestDataReceived = new EventEmitter<{ nodeId: string; data: any }>();
  @Output() close = new EventEmitter<void>();
  @Output() deleteNode = new EventEmitter<void>();
  @Output() navigateToNode = new EventEmitter<string>();
  @Output() nodeExecuted = new EventEmitter<{ nodeId: string; data: any }>();
  @Output() nameChanged = new EventEmitter<string>();

  @ViewChild('inputSearchEl') inputSearchEl?: ElementRef<HTMLInputElement>;
  @ViewChild('outputSearchEl') outputSearchEl?: ElementRef<HTMLInputElement>;
  @ViewChild('nameInput') nameInputEl?: ElementRef<HTMLInputElement>;

  activeTab: 'parameters' | 'settings' = 'parameters';

  // Name editing state
  editingName = false;
  editNameValue = '';

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

  // Ancestor node selector for input column
  selectedInputNodeId: string | null = null;

  // Multi-output tab state
  selectedOutputIndex = 0;

  // Pin / edit output state
  isEditingOutput = false;
  editOutputJson = '';
  editOutputError = '';

  // Node execution state
  isNodeExecuting = false;
  private _singleNodeError: string | null = null;

  get nodeExecutionError(): string | null {
    if (this._singleNodeError) return this._singleNodeError;
    // Check workflow execution data for this node's error
    const raw = this.executionData?.[this.node?.id];
    if (!raw) return null;
    const run = Array.isArray(raw) ? raw[0] : raw;
    return run?.error || null;
  }

  // Support email (from settings)
  supportEmail = '';

  // Webhook path validation
  webhookPathError = '';

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
    this.settingsService.getSettings().subscribe(settings => {
      this.supportEmail = settings.supportEmail || '';
      if (this.isWebhookNode) {
        if (this.projectContextPath) {
          const prodUrl = settings.webhookUrlProduction || '';
          const baseUrl = this.extractBaseUrl(prodUrl);
          this.webhookUrlProduction = baseUrl + '/' + this.projectContextPath + '/';
          this.webhookUrlTest = baseUrl + '/' + this.projectContextPath + '-test/';
        } else {
          this.webhookUrlProduction = settings.webhookUrlProduction || '';
          this.webhookUrlTest = settings.webhookUrlTest || '';
        }
      }
    });
  }

  private extractBaseUrl(url: string): string {
    try {
      const parsed = new URL(url);
      return parsed.origin;
    } catch {
      // Fallback: strip everything after scheme+host+port
      const match = url.match(/^(https?:\/\/[^/]+)/);
      return match ? match[1] : url.replace(/\/+$/, '');
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['node'] && changes['node'].currentValue?.id !== changes['node'].previousValue?.id) {
      this.selectedOutputIndex = 0;
      // Default to first direct parent node
      const parents = this.previousNodes;
      this.selectedInputNodeId = parents.length > 0 ? parents[0].node.id : null;
    }
  }

  /** Whether the current node has multiple main outputs (e.g. Cache, If, Filter, Switch) */
  get isMultiOutput(): boolean {
    const outputs = this.nodeType?.outputs || [];
    return outputs.filter(o => o.type === 'main').length > 1;
  }

  /** Display names for each main output (e.g. ['Hit', 'Miss'] for Cache node) */
  get mainOutputNames(): string[] {
    return (this.nodeType?.outputs || [])
      .filter(o => o.type === 'main')
      .map(o => o.displayName || o.name);
  }

  /** Item count for each main output slot (for the tab badges) */
  get outputSlotCounts(): number[] {
    const data = this.effectiveOutputData;
    if (!data) return [];
    const outputs = this.extractOutputArray(data);
    if (!outputs) return [];
    return outputs.map(out => Array.isArray(out) ? out.length : 0);
  }

  get isWebhookNode(): boolean {
    return this.node?.type === 'webhook';
  }

  get webhookPath(): string {
    return this._localPath ?? this.node?.parameters?.['path'] ?? '';
  }

  private joinWebhookUrl(base: string, path: string): string {
    const joined = base.replace(/\/+$/, '') + '/' + path.replace(/^\/+/, '');
    // Collapse any double slashes in the path portion (preserve ://)
    return joined.replace(/([^:])\/\/+/g, '$1/');
  }

  get fullWebhookUrl(): string {
    return this.joinWebhookUrl(this.webhookUrlProduction, this.webhookPath);
  }

  get fullWebhookTestUrl(): string {
    return this.joinWebhookUrl(this.webhookUrlTest, this.webhookPath);
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

  hasParameter(name: string): boolean {
    return this.parameters.some(p => p.name === name);
  }

  get visibleParameters(): NodeParameter[] {
    return this.parameters.filter(p => !p.isNodeSetting && this.isVisible(p));
  }

  /** Returns only the credential type(s) currently relevant based on the node's parameter values. */
  get activeCredentialTypes(): string[] {
    const allTypes = this.nodeType?.credentials || [];
    if (allTypes.length <= 1) return allTypes;

    // Only apply authentication-based filtering if the node defines an 'authentication' parameter
    const hasAuthParam = this.parameters.some(p => p.name === 'authentication');
    if (hasAuthParam) {
      const authValue = this.node.parameters?.['authentication'];
      if (authValue === 'none' || !authValue) return [];
      if (authValue === 'genericCredentialType') {
        const genericType = this.node.parameters?.['genericAuthType'];
        return genericType ? [genericType] : [];
      }
      if (authValue === 'predefinedCredentialType') {
        const predefined = this.node.parameters?.['nodeCredentialType'];
        return predefined ? [predefined] : [];
      }
    }

    // General fallback: check if any parameter value matches a credential type name
    const paramValues = Object.values(this.node.parameters || {});
    const match = allTypes.find(t => paramValues.includes(t));
    if (match) return [match];

    return allTypes;
  }

  /** Returns the parameter name after which the credential dropdown should appear, or null for top position. */
  get credentialInsertAfterParam(): string | null {
    const authValue = this.node.parameters?.['authentication'];
    if (authValue === 'predefinedCredentialType') return 'nodeCredentialType';
    if (authValue === 'genericCredentialType') return 'genericAuthType';
    return null;
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

  /** All ancestor nodes via BFS traversal, closest first, with depth */
  getAncestorNodes(): { node: WorkflowNode; nodeType?: NodeTypeDescription; depth: number }[] {
    const results: { node: WorkflowNode; nodeType?: NodeTypeDescription; depth: number }[] = [];
    const visited = new Set<string>();
    const queue: { id: string; depth: number }[] = [{ id: this.node.id, depth: 0 }];
    visited.add(this.node.id);

    while (queue.length > 0) {
      const { id: currentId, depth } = queue.shift()!;
      // Find all source nodes that connect into currentId
      for (const [sourceId, conn] of Object.entries(this.connections)) {
        if (!conn?.main) continue;
        for (const outputs of conn.main) {
          if (!outputs) continue;
          for (const c of outputs) {
            if (c.node === currentId && !visited.has(sourceId)) {
              visited.add(sourceId);
              const n = this.allNodes.find(nd => nd.id === sourceId);
              if (n) {
                results.push({ node: n, nodeType: this.nodeTypeMap.get(n.type), depth: depth + 1 });
                queue.push({ id: sourceId, depth: depth + 1 });
              }
            }
          }
        }
      }
    }
    return results;
  }

  /** Returns the nodes to display in the input column based on the selected ancestor */
  get selectedInputNodes(): { node: WorkflowNode; nodeType?: NodeTypeDescription }[] {
    if (!this.selectedInputNodeId) return this.previousNodes;
    const ancestor = this.allNodes.find(n => n.id === this.selectedInputNodeId);
    if (!ancestor) return this.previousNodes;
    return [{ node: ancestor, nodeType: this.nodeTypeMap.get(ancestor.type) }];
  }

  get nodeExecutionData(): any {
    return this.executionData?.[this.node.id] ?? null;
  }

  get outputJson(): string {
    const data = this.effectiveOutputData;
    if (!data) return '';
    return JSON.stringify(this.extractItems(data, this.selectedOutputIndex), null, 2);
  }

  getNodeOutputJson(nodeId: string): string {
    const data = this.executionData?.[nodeId];
    if (!data) return '';
    return JSON.stringify(this.extractItems(data), null, 2);
  }

  /** Resolve the effective output data for a previous node: pinned > execution > webhook */
  private getPreviousNodeData(nodeId: string): any {
    return this.pinData?.[nodeId] ?? this.executionData?.[nodeId] ?? this.webhookTestData?.[nodeId];
  }

  get inputItemCount(): number {
    let count = 0;
    for (const prev of this.previousNodes) {
      const data = this.getPreviousNodeData(prev.node.id);
      if (data) count += this.extractItems(data).length;
    }
    return count;
  }

  get outputItemCount(): number {
    const data = this.effectiveOutputData;
    if (!data) return 0;
    return this.extractItems(data, this.selectedOutputIndex).length;
  }

  isVisible(param: NodeParameter): boolean {
    if (!param.displayOptions) return true;
    const show = param.displayOptions.show;
    if (!show) return true;

    return Object.entries(show).every(([key, values]: [string, any]) => {
      let currentVal = this.node.parameters[key];
      // Fall back to the parameter's default value when not yet explicitly set
      if (currentVal === undefined) {
        const paramDef = this.parameters.find(p => p.name === key);
        if (paramDef) currentVal = paramDef.defaultValue;
      }
      if (Array.isArray(values)) {
        return values.includes(currentVal);
      }
      return currentVal === values;
    });
  }

  onParameterChange(name: string, value: any): void {
    if (name === 'path' && this.isWebhookNode) {
      this._localPath = value;
      this.webhookPathError = '';
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
      // Validate webhook path uniqueness
      const pathVal = this._localPath ?? this.node?.parameters?.['path'] ?? '';
      if (pathVal) {
        const method = this.currentHttpMethod;
        this.webhookTestService.validatePath(this.workflowId, method, pathVal).subscribe({
          next: (res) => { this.webhookPathError = res.available ? '' : (res.message || 'Path already in use'); },
          error: () => { this.webhookPathError = ''; }
        });
      }
    }

    // Expression validation on blur
    const value = String(this.node.parameters?.[name] ?? '');
    if (value.includes('{{')) {
      const inputData = this.collectInputForExecution();
      this.workflowService.evaluateExpression(value, inputData).subscribe({
        next: (res) => { this.expressionErrors[name] = res.error || ''; },
        error: (err) => { this.expressionErrors[name] = err.message || 'Validation failed'; }
      });
    } else {
      delete this.expressionErrors[name];
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
   * Extract the multi-output array from any execution data shape.
   * Returns the array of output slot arrays, or null if the format is flat/unknown.
   */
  extractOutputArray(rawData: any): any[][] | null {
    if (!Array.isArray(rawData) || rawData.length === 0) return null;
    const first = rawData[0];

    // Live execution format: [{ status, data: [[...], [...]], ... }]
    if (first && typeof first === 'object' && !Array.isArray(first) && 'data' in first) {
      const data = first.data;
      // data: {main: [[...], [...]]} — completed execution from buildResultData
      if (data?.main && Array.isArray(data.main) && data.main.length > 0 && Array.isArray(data.main[0])) {
        return data.main;
      }
      // data: [[...], [...]] — live WebSocket execution
      if (Array.isArray(data) && data.length > 0 && Array.isArray(data[0])) {
        return data;
      }
    }

    // Direct format: [[{json: ...}], [{json: ...}]]
    if (Array.isArray(first)) {
      return rawData;
    }

    return null;
  }

  /**
   * Extracts flat row data from execution data for a specific output index.
   * For multi-output nodes, pass the output index to select which output to show.
   */
  extractItems(rawData: any, outputIndex: number = 0): Record<string, any>[] {
    if (!rawData) return [];

    const outputs = this.extractOutputArray(rawData);
    if (outputs) {
      const selected = outputs[outputIndex] ?? [];
      return selected.map((item: any) => item?.json ?? item).filter((item: any) => item != null);
    }

    // Flat array: [{json: ...}] or [plainObjects]
    let items: any[] = [];
    if (Array.isArray(rawData)) {
      items = rawData;
    } else if (typeof rawData === 'object') {
      return [rawData];
    }
    return items.map((item: any) => item?.json ?? item).filter((item: any) => item != null);
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

  // --- Table cell expansion helpers ---

  /** Returns true if a cell value is a non-empty object that should be expanded into key-value rows */
  isExpandableCell(value: any): boolean {
    return value !== null && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length > 0;
  }

  /** Returns a label for empty/null cell values */
  getEmptyCellLabel(value: any): string {
    if (value === null || value === undefined) return '';
    if (Array.isArray(value) && value.length === 0) return '{empty array}';
    if (typeof value === 'object' && Object.keys(value).length === 0) return '{empty object}';
    return '';
  }

  /** Builds flat key-value entries for an expandable object cell */
  getCellEntries(value: any, columnName: string): { key: string; path: string; displayValue: string; isEmpty: boolean }[] {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
    const entries: { key: string; path: string; displayValue: string; isEmpty: boolean }[] = [];
    for (const [key, val] of Object.entries(value)) {
      const path = columnName + '.' + key;
      if (val === null || val === undefined) {
        entries.push({ key, path, displayValue: String(val), isEmpty: false });
      } else if (Array.isArray(val)) {
        entries.push({ key, path, displayValue: val.length === 0 ? '{empty array}' : JSON.stringify(val), isEmpty: val.length === 0 });
      } else if (typeof val === 'object') {
        const keys = Object.keys(val);
        entries.push({ key, path, displayValue: keys.length === 0 ? '{empty object}' : JSON.stringify(val), isEmpty: keys.length === 0 });
      } else {
        entries.push({ key, path, displayValue: String(val), isEmpty: false });
      }
    }
    return entries;
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
    for (const prev of this.selectedInputNodes) {
      const data = this.getPreviousNodeData(prev.node.id);
      if (data) items.push(...this.extractItems(data));
    }
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.inputSchemaCollapsed, this.inputSearch);
  }

  get outputSchemaNodes(): SchemaNode[] {
    const data = this.effectiveOutputData;
    if (!data) return [];
    const items = this.extractItems(data, this.selectedOutputIndex);
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.outputSchemaCollapsed, this.outputSearch);
  }

  // --- Table helpers ---

  get inputTableRows(): Record<string, any>[] {
    const allRows: Record<string, any>[] = [];
    for (const prev of this.selectedInputNodes) {
      const data = this.getPreviousNodeData(prev.node.id);
      if (data) allRows.push(...this.extractItems(data));
    }
    return this.filterRows(allRows, this.inputSearch);
  }

  get inputTableHeaders(): string[] {
    return this.extractHeaders(this.inputTableRows);
  }

  get outputTableRows(): Record<string, any>[] {
    const data = this.effectiveOutputData;
    if (!data) return [];
    return this.filterRows(this.extractItems(data, this.selectedOutputIndex), this.outputSearch);
  }

  get outputTableHeaders(): string[] {
    return this.extractHeaders(this.outputTableRows);
  }

  // --- JSON helpers ---

  getFilteredInputJson(nodeId: string): string {
    const data = this.getPreviousNodeData(nodeId);
    if (!data) return '';
    const items = this.extractItems(data);
    if (!this.inputSearch) return JSON.stringify(items, null, 2);
    const filtered = this.filterRows(items, this.inputSearch);
    return filtered.length > 0 ? JSON.stringify(filtered, null, 2) : '';
  }

  /** Get parsed input items for a node (for structured JSON rendering) */
  getInputItems(nodeId: string): Record<string, any>[] {
    const data = this.getPreviousNodeData(nodeId);
    if (!data) return [];
    const items = this.extractItems(data);
    if (!this.inputSearch) return items;
    return this.filterRows(items, this.inputSearch);
  }

  /** Return all entries of an object as [key, formattedValue] pairs */
  objectEntries(obj: Record<string, any>): [string, string][] {
    if (!obj || typeof obj !== 'object') return [];
    return Object.entries(obj).map(([k, v]) => [k, this.formatCell(v)]);
  }

  /**
   * Build a flat list of "JSON tree lines" for rendering a JSON-like view
   * where every key at every depth is draggable with its full $json path.
   */
  buildJsonTreeLines(items: Record<string, any>[]): JsonTreeLine[] {
    const lines: JsonTreeLine[] = [];
    const isMulti = items.length > 1;
    if (isMulti) {
      lines.push({ indent: 0, type: 'bracket', bracket: '[' });
    }
    items.forEach((item, idx) => {
      const baseIndent = isMulti ? 1 : 0;
      if (isMulti) {
        lines.push({ indent: baseIndent, type: 'bracket', bracket: '{' });
      } else {
        lines.push({ indent: 0, type: 'bracket', bracket: '{' });
      }
      this.appendObjectLines(lines, item, '', baseIndent + 1, Object.keys(item).length);
      const closeBracket = isMulti
        ? (idx < items.length - 1 ? '},' : '}')
        : '}';
      lines.push({ indent: baseIndent, type: 'bracket', bracket: closeBracket });
    });
    if (isMulti) {
      lines.push({ indent: 0, type: 'bracket', bracket: ']' });
    }
    return lines;
  }

  private appendObjectLines(
    lines: JsonTreeLine[], obj: Record<string, any>,
    parentPath: string, indent: number, totalKeys: number
  ): void {
    const keys = Object.keys(obj);
    keys.forEach((key, i) => {
      const path = parentPath ? `${parentPath}.${key}` : key;
      const value = obj[key];
      const isLast = i === totalKeys - 1;
      const comma = isLast ? '' : ',';

      if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
        const childKeys = Object.keys(value);
        if (childKeys.length === 0) {
          lines.push({ indent, type: 'keyValue', key, path, displayValue: '{}' + comma, valueType: 'object' });
        } else {
          lines.push({ indent, type: 'keyOpen', key, path, bracket: '{' });
          this.appendObjectLines(lines, value, path, indent + 1, childKeys.length);
          lines.push({ indent, type: 'bracket', bracket: '}' + comma });
        }
      } else if (Array.isArray(value)) {
        if (value.length === 0) {
          lines.push({ indent, type: 'keyValue', key, path, displayValue: '[]' + comma, valueType: 'array' });
        } else {
          lines.push({ indent, type: 'keyOpen', key, path, bracket: '[' });
          this.appendArrayLines(lines, value, path, indent + 1);
          lines.push({ indent, type: 'bracket', bracket: ']' + comma });
        }
      } else {
        const formatted = this.formatJsonValue(value);
        lines.push({ indent, type: 'keyValue', key, path, displayValue: formatted + comma, valueType: this.typeOf(value) });
      }
    });
  }

  private appendArrayLines(
    lines: JsonTreeLine[], arr: any[],
    parentPath: string, indent: number
  ): void {
    arr.forEach((item, idx) => {
      const isLast = idx === arr.length - 1;
      const comma = isLast ? '' : ',';
      const itemPath = `${parentPath}[${idx}]`;

      if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
        const childKeys = Object.keys(item);
        if (childKeys.length === 0) {
          lines.push({ indent, type: 'bracket', bracket: '{}' + comma });
        } else {
          lines.push({ indent, type: 'bracket', bracket: '{' });
          this.appendObjectLines(lines, item, itemPath, indent + 1, childKeys.length);
          lines.push({ indent, type: 'bracket', bracket: '}' + comma });
        }
      } else if (Array.isArray(item)) {
        lines.push({ indent, type: 'bracket', bracket: '[' });
        this.appendArrayLines(lines, item, itemPath, indent + 1);
        lines.push({ indent, type: 'bracket', bracket: ']' + comma });
      } else {
        const formatted = this.formatJsonValue(item);
        lines.push({ indent, type: 'value', displayValue: formatted + comma, valueType: this.typeOf(item) });
      }
    });
  }

  private formatJsonValue(value: any): string {
    if (value === null) return 'null';
    if (value === undefined) return 'undefined';
    if (typeof value === 'string') return '"' + value + '"';
    return String(value);
  }

  /** Cache for JSON tree lines per node to avoid recalculating on every render */
  private jsonTreeCache = new Map<string, { items: Record<string, any>[]; lines: JsonTreeLine[] }>();

  getJsonTreeLines(nodeId: string): JsonTreeLine[] {
    const items = this.getInputItems(nodeId);
    const cached = this.jsonTreeCache.get(nodeId);
    if (cached && cached.items === items) return cached.lines;
    const lines = this.buildJsonTreeLines(items);
    this.jsonTreeCache.set(nodeId, { items, lines });
    return lines;
  }

  get filteredOutputJson(): string {
    const data = this.effectiveOutputData;
    if (!data) return '';
    const items = this.extractItems(data, this.selectedOutputIndex);
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
    return !!(this.isPinned || this.receivedTestData || this.nodeExecutionData);
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

  get isStickyNote(): boolean {
    return this.node?.type === 'stickyNote';
  }

  /** Collect input items from upstream nodes in {json: ...} format for the backend */
  private collectInputForExecution(): any[] {
    const items: any[] = [];
    for (const prev of this.previousNodes) {
      const data = this.getPreviousNodeData(prev.node.id);
      items.push(...this.extractRawItems(data));
    }
    return items;
  }

  /**
   * Extract raw items in {json: ...} format from any execution data shape.
   * Unlike extractItems() which unwraps json, this keeps the {json: ...} wrapper
   * because the backend expression evaluator expects that format.
   */
  private extractRawItems(rawData: any, outputIndex: number = 0): any[] {
    if (!rawData) return [];

    const outputs = this.extractOutputArray(rawData);
    if (outputs) {
      const selected = outputs[outputIndex] ?? [];
      return selected.map((item: any) => {
        if (item && typeof item === 'object' && 'json' in item) return item;
        return { json: item };
      });
    }

    // Flat array: [{json: ...}] or [{key: val}]
    if (Array.isArray(rawData)) {
      return rawData.map(item => {
        if (item && typeof item === 'object' && 'json' in item) return item;
        return { json: item };
      });
    }

    // Single object
    if (typeof rawData === 'object') {
      return [{ json: rawData }];
    }

    return [];
  }

  executeNode(): void {
    if (this.isNodeExecuting || !this.node) return;
    this.isNodeExecuting = true;
    this._singleNodeError = null;
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
          this._singleNodeError = res.error;
        }
        this.nodeExecuted.emit({ nodeId: this.node.id, data: res.output });
      },
      error: (err) => {
        this.isNodeExecuting = false;
        this._singleNodeError = err.message || 'Execution failed';
      }
    });
  }

  // --- Pin / Edit output ---

  get isPinned(): boolean {
    return !!(this.pinData && this.node?.id && this.pinData[this.node.id]);
  }

  get pinnedItems(): any[] {
    if (!this.isPinned) return [];
    return this.pinData[this.node.id] || [];
  }

  /** Returns pinned data in the raw format expected by extractItems */
  private get pinnedRawData(): any {
    if (!this.isPinned) return null;
    return this.pinnedItems;
  }

  /** Effective output: pinned data takes priority over execution data */
  private get effectiveOutputData(): any {
    return this.pinnedRawData ?? this.receivedTestData ?? this.nodeExecutionData;
  }

  onPinOutput(): void {
    if (!this.node?.id) return;
    const data = this.receivedTestData ?? this.nodeExecutionData;
    if (!data) return;
    // Convert to [{json: ...}] format for storage
    const items = this.extractItems(data, this.selectedOutputIndex);
    const pinItems = items.map(item => ({ json: item }));
    this.pinDataChanged.emit({ nodeId: this.node.id, items: pinItems });
  }

  onUnpinOutput(): void {
    if (!this.node?.id) return;
    this.isEditingOutput = false;
    this.pinDataChanged.emit({ nodeId: this.node.id, items: null });
  }

  onStartEdit(): void {
    const data = this.effectiveOutputData;
    const items = data ? this.extractItems(data, this.selectedOutputIndex) : [];
    const pinFormatted = items.map(item => ({ json: item }));
    this.editOutputJson = JSON.stringify(pinFormatted, null, 2);
    this.editOutputError = '';
    this.isEditingOutput = true;
  }

  onSaveEdit(): void {
    if (!this.node?.id) return;
    try {
      const parsed = JSON.parse(this.editOutputJson);
      if (!Array.isArray(parsed)) {
        this.editOutputError = 'Data must be a JSON array';
        return;
      }
      // Ensure each item has {json: ...} wrapper
      const items = parsed.map((item: any) => {
        if (item && typeof item === 'object' && 'json' in item) return item;
        return { json: item };
      });
      this.pinDataChanged.emit({ nodeId: this.node.id, items });
      this.isEditingOutput = false;
      this.editOutputError = '';
    } catch (e: any) {
      this.editOutputError = 'Invalid JSON: ' + (e.message || 'parse error');
    }
  }

  onCancelEdit(): void {
    this.isEditingOutput = false;
    this.editOutputError = '';
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
    if (this.expressionEditorOpen) {
      this.expressionEditorOpen = false;
      return;
    }
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close.emit();
    }
  }

  // --- Name editing ---

  startEditingName(): void {
    if (this.readOnly) return;
    this.editingName = true;
    this.editNameValue = this.node.name;
    setTimeout(() => this.nameInputEl?.nativeElement.focus());
  }

  finishEditingName(): void {
    if (!this.editingName) return;
    this.editingName = false;
    const trimmed = this.editNameValue.trim();
    if (trimmed && trimmed !== this.node.name) {
      this.nameChanged.emit(trimmed);
    }
  }

  cancelEditingName(): void {
    this.editingName = false;
  }

  // --- Expression validation ---

  expressionErrors: Record<string, string> = {};

  onParamFocus(name: string): void {
    delete this.expressionErrors[name];
  }

  getExpressionError(name: string): string {
    return this.expressionErrors[name] || '';
  }

  // --- Expression editor modal ---

  expressionEditorOpen = false;
  expressionEditorParam = '';
  expressionEditorValue = '';
  expressionEditorInputItems: any[] = [];
  expressionEditorAncestorNodes: ExpressionAncestorNode[] = [];
  expressionEditorNodeDataMap: Record<string, any[]> = {};
  expressionEditorNodeOutputs: Record<string, any> = {};

  // For fixedCollection nested expression editing
  private fcExpressionContext: { paramName: string; index: number; fieldName: string } | null = null;

  /** Build ancestor info and data maps for the expression editor */
  private prepareExpressionEditorAncestors(): void {
    const directParentIds = new Set(this.previousNodes.map(p => p.node.id));
    const ancestors = this.getAncestorNodes();
    this.expressionEditorAncestorNodes = ancestors.map(a => ({
      id: a.node.id,
      name: a.node.name,
      isDirectParent: directParentIds.has(a.node.id),
      depth: a.depth
    }));
    this.expressionEditorNodeDataMap = {};
    this.expressionEditorNodeOutputs = {};
    for (const anc of ancestors) {
      const data = this.getPreviousNodeData(anc.node.id);
      if (data) {
        const rawItems = this.extractRawItems(data);
        this.expressionEditorNodeDataMap[anc.node.id] = rawItems;
        // Build nodeOutputs keyed by node name (first item's json data)
        const items = this.extractItems(data);
        if (items.length > 0) {
          this.expressionEditorNodeOutputs[anc.node.name] = items[0];
        }
      }
    }
  }

  openExpressionEditor(paramName: string, currentValue: any): void {
    this.fcExpressionContext = null;
    this.expressionEditorParam = paramName;
    this.expressionEditorValue = String(currentValue ?? '');
    this.expressionEditorInputItems = this.collectInputForExecution();
    this.prepareExpressionEditorAncestors();
    this.expressionEditorOpen = true;
  }

  openFixedCollectionExpressionEditor(paramName: string, event: FixedCollectionExpressionEvent): void {
    this.fcExpressionContext = { paramName, index: event.index, fieldName: event.fieldName };
    this.expressionEditorParam = paramName + '[' + event.index + '].' + event.fieldName;
    this.expressionEditorValue = event.value;
    this.expressionEditorInputItems = this.collectInputForExecution();
    this.prepareExpressionEditorAncestors();
    this.expressionEditorOpen = true;
  }

  onExpressionEditorSave(expression: string): void {
    if (this.fcExpressionContext) {
      const { paramName, index, fieldName } = this.fcExpressionContext;
      const currentItems = Array.isArray(this.node.parameters[paramName])
        ? [...this.node.parameters[paramName].map((item: any) => ({ ...item }))]
        : [];
      if (currentItems[index]) {
        currentItems[index][fieldName] = expression;
        this.onParameterChange(paramName, currentItems);
      }
      this.fcExpressionContext = null;
    } else {
      this.onParameterChange(this.expressionEditorParam, expression);
    }
    this.expressionEditorOpen = false;
  }

  onExpressionEditorClose(): void {
    this.expressionEditorOpen = false;
  }

  /** Returns the expression prefix for the currently selected input node */
  private get inputExpressionPrefix(): string {
    if (!this.selectedInputNodeId) return '$json.';
    const isDirectParent = this.previousNodes.some(p => p.node.id === this.selectedInputNodeId);
    if (isDirectParent) return '$json.';
    const node = this.allNodes.find(n => n.id === this.selectedInputNodeId);
    if (!node) return '$json.';
    return '$node["' + node.name + '"].json.';
  }

  onSchemaDragStart(event: DragEvent, node: SchemaNode): void {
    if (node.children && node.children.length > 0) return;
    event.dataTransfer?.setData('text/plain', '{{' + this.inputExpressionPrefix + node.path + '}}');
    event.dataTransfer!.effectAllowed = 'copy';
  }

  onFieldDragStart(event: DragEvent, fieldName: string): void {
    event.dataTransfer?.setData('text/plain', '{{' + this.inputExpressionPrefix + fieldName + '}}');
    event.dataTransfer!.effectAllowed = 'copy';
  }

  get allNodeNames(): string[] {
    return this.allNodes.map(n => n.name);
  }

  get supportMailtoUrl(): string {
    const category = this.nodeType?.category || '';
    const displayName = this.nodeType?.displayName || this.node?.type || '';
    const subject = encodeURIComponent(`[${category}] ${displayName}`);
    return `mailto:${this.supportEmail}?subject=${subject}`;
  }

  /** Get all unique top-level field names from input items for a given node */
  getInputFieldNames(nodeId: string): string[] {
    const data = this.getPreviousNodeData(nodeId);
    if (!data) return [];
    const items = this.extractItems(data);
    return this.extractHeaders(items);
  }
}
