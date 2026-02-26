import { Component, Input, Output, EventEmitter, ElementRef, ViewChild, AfterViewChecked, SimpleChanges, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ChatService } from '../../../../core/services/chat.service';
import { ChatMessage } from '../../../../core/models/chat.model';
import { WorkflowNode } from '../../../../core/models';

export interface LogsNodeEntry {
  node: WorkflowNode;
  displayName: string;
  iconName: string;
  status: string;
  duration: number;
  itemCount: number;
}

export interface SchemaNode {
  key: string;
  type: string;
  value: string;
  path: string;
  level: number;
  children?: SchemaNode[];
  expanded?: boolean;
}

const ICON_PATHS: Record<string, string> = {
  'globe': '<circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/>',
  'merge': '<path d="m8 6 4-4 4 4"/><path d="M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22"/><path d="m20 22-5-5"/>',
  'arrow-right': '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
  'split': '<path d="M16 3h5v5"/><path d="M8 3H3v5"/><path d="M12 22v-8.3a4 4 0 0 0-1.172-2.872L3 3"/><path d="m15 9 6-6"/>',
  'clock': '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  'play': '<polygon points="6 3 20 12 6 21 6 3"/>',
  'webhook': '<path d="M18 16.98h-5.99c-1.1 0-1.95.94-2.48 1.9A4 4 0 0 1 2 17c.01-.7.2-1.4.57-2"/><path d="m6 17 3.13-5.78c.53-.97.1-2.18-.5-3.1a4 4 0 1 1 6.89-4.06"/><path d="m12 6 3.13 5.73C15.66 12.7 16.9 13 18 13a4 4 0 0 1 0 8"/>',
  'reply': '<polyline points="9 17 4 12 9 7"/><path d="M20 18v-2a4 4 0 0 0-4-4H4"/>',
  'unfold-vertical': '<path d="M12 22v-6"/><path d="M12 8V2"/><path d="M4 12H2"/><path d="M10 12H8"/><path d="M16 12h-2"/><path d="M22 12h-2"/><path d="m15 19-3 3-3-3"/><path d="m15 5-3-3-3 3"/>',
  'route': '<circle cx="6" cy="19" r="3"/><path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"/><circle cx="18" cy="5" r="3"/>',
  'pen': '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
  'code': '<polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>',
};

@Component({
  selector: 'app-editor-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './editor-drawer.component.html',
  styleUrl: './editor-drawer.component.scss'
})
export class EditorDrawerComponent implements AfterViewChecked, OnChanges {
  @Input() executionData: Record<string, any> | null = null;
  @Input() selectedNodeId: string | null = null;
  @Input() isExecuting = false;
  @Input() workflowId: string | null = null;

  @Input() expanded = false;
  @Output() expandedChange = new EventEmitter<boolean>();

  // Logs inputs
  @Input() executionNodes: WorkflowNode[] = [];
  @Input() nodeTypeMap: Map<string, any> = new Map();
  @Input() executionStatus = '';
  @Input() autoExpandOnView = true;

  @Input() set viewingExecution(value: boolean) {
    if (value && !this._viewingExecution) {
      if (this.autoExpandOnView) {
        this.activeTab = 'logs';
        this.selectedLogsNodeId = null;
        if (!this.expanded) {
          this.expanded = true;
          this.expandedChange.emit(true);
        }
      }
    } else if (!value && this._viewingExecution) {
      if (this.activeTab === 'logs') {
        this.activeTab = 'output';
      }
      this.selectedLogsNodeId = null;
    }
    this._viewingExecution = value;
  }
  get viewingExecution(): boolean { return this._viewingExecution; }
  private _viewingExecution = false;

  @ViewChild('chatMessages') chatMessagesEl?: ElementRef<HTMLDivElement>;

  activeTab: 'output' | 'chat' | 'logs' = 'output';
  drawerHeight = 300;
  private minHeight = 150;
  private maxHeightPercent = 0.8;
  private dragging = false;
  private startY = 0;
  private startHeight = 0;

  chatInput = '';
  messages: ChatMessage[] = [];
  isTyping = false;
  private shouldScrollChat = false;
  private chatSessionId: string | null = null;

  logsOverviewWidth = 280;
  private logsResizing = false;
  private logsResizeStartX = 0;
  private logsResizeStartWidth = 0;

  selectedLogsNodeId: string | null = null;
  detailView: 'input' | 'output' = 'output';
  displayMode: 'schema' | 'table' | 'json' = 'table';
  schemaSearch = '';
  searchExpanded = false;
  schemaCollapsed = new Set<string>();
  @ViewChild('drawerSearchEl') drawerSearchEl?: ElementRef<HTMLInputElement>;
  @Output() openNodeDetail = new EventEmitter<string>();
  @Output() clearExecution = new EventEmitter<void>();
  private iconCache = new Map<string, SafeHtml>();

  constructor(
    private chatService: ChatService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['executionData'] && this._viewingExecution && !this.selectedLogsNodeId) {
      const nodes = this.logsNodeList;
      if (nodes.length > 0) {
        this.selectedLogsNodeId = nodes[0].node.id;
      }
    }
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollChat && this.chatMessagesEl) {
      const el = this.chatMessagesEl.nativeElement;
      el.scrollTop = el.scrollHeight;
      this.shouldScrollChat = false;
    }
  }

  // ── Output tab ──

  get nodeExecutionData(): any {
    if (!this.executionData || !this.selectedNodeId) return null;
    return this.executionData[this.selectedNodeId];
  }

  get outputJson(): string {
    const data = this.nodeExecutionData;
    if (!data) return '';
    return JSON.stringify(data.data || data, null, 2);
  }

  get executionStatusLabel(): string {
    return this.nodeExecutionData?.status || (this.isExecuting ? 'running' : 'idle');
  }

  // ── Logs tab ──

  /** Extract main output items from either Executions-tab format ({ main: [[...]] }) or editor-tab WebSocket format ([[...]]) */
  private getRunMainOutput(run: any): any[] | null {
    if (!run?.data) return null;
    // Executions-tab: run.data = { main: [[items...]] }
    const fromMain = run.data?.main?.[0];
    if (Array.isArray(fromMain)) return fromMain;
    // Editor-tab WebSocket: run.data = [[items...]]
    if (Array.isArray(run.data) && Array.isArray(run.data[0])) return run.data[0];
    return null;
  }

  /** Extract main input items from either data format */
  private getRunMainInput(run: any): any[] | null {
    if (!run?.inputData) return null;
    const fromMain = run.inputData?.main?.[0];
    if (Array.isArray(fromMain)) return fromMain;
    if (Array.isArray(run.inputData) && Array.isArray(run.inputData[0])) return run.inputData[0];
    return null;
  }

  get logsNodeList(): LogsNodeEntry[] {
    if (!this.executionNodes?.length || !this.executionData) return [];

    const entries: LogsNodeEntry[] = [];

    for (const node of this.executionNodes) {
      const raw = this.executionData[node.id];
      if (!raw) continue;

      const typeDesc = this.nodeTypeMap?.get(node.type);
      const run = Array.isArray(raw) ? raw[0] : raw;
      if (!run || (run.startedAt == null && run.executionTime == null && !run.status)) continue;
      const status = run?.error ? 'error' : (run?.status || 'success');
      const duration = run?.executionTime || 0;
      const mainOutput = this.getRunMainOutput(run);
      const itemCount = run?.itemCount ?? (Array.isArray(mainOutput) ? mainOutput.length : 0);

      entries.push({
        node,
        displayName: node.name,
        iconName: typeDesc?.icon || '',
        status,
        duration,
        itemCount,
      });
    }

    entries.sort((a, b) => {
      const aRaw = this.executionData![a.node.id];
      const bRaw = this.executionData![b.node.id];
      const aRun = Array.isArray(aRaw) ? aRaw[0] : aRaw;
      const bRun = Array.isArray(bRaw) ? bRaw[0] : bRaw;
      const aOrder = aRun?.executionOrder ?? Number.MAX_SAFE_INTEGER;
      const bOrder = bRun?.executionOrder ?? Number.MAX_SAFE_INTEGER;
      return aOrder - bOrder;
    });

    return entries;
  }

  get overallDurationMs(): number {
    if (!this.executionData) return 0;
    let total = 0;
    for (const nodeId of Object.keys(this.executionData)) {
      const raw = this.executionData[nodeId];
      const run = Array.isArray(raw) ? raw[0] : raw;
      total += run?.executionTime || 0;
    }
    return total;
  }

  get overallSummaryText(): string {
    const status = this.capitalizeStatus(this.executionStatus || 'success');
    const duration = this.formatMs(this.overallDurationMs);
    return `${status} in ${duration}`;
  }

  get selectedLogsEntry(): LogsNodeEntry | null {
    if (!this.selectedLogsNodeId) return null;
    return this.logsNodeList.find(e => e.node.id === this.selectedLogsNodeId) || null;
  }

  get selectedNodeOutputJson(): string {
    if (!this.selectedLogsNodeId || !this.executionData) return '';
    const raw = this.executionData[this.selectedLogsNodeId];
    if (!raw) return '';

    const run = Array.isArray(raw) ? raw[0] : raw;
    const mainOutput = this.getRunMainOutput(run);
    if (Array.isArray(mainOutput)) {
      const items = mainOutput.map((item: any) => item?.json || item || {});
      return JSON.stringify(items, null, 2);
    }
    return JSON.stringify(run?.data || run, null, 2);
  }

  get selectedNodeOutputItems(): Record<string, any>[] {
    if (!this.selectedLogsNodeId || !this.executionData) return [];
    const raw = this.executionData[this.selectedLogsNodeId];
    if (!raw) return [];
    const run = Array.isArray(raw) ? raw[0] : raw;
    const mainOutput = this.getRunMainOutput(run);
    if (!Array.isArray(mainOutput)) return [];
    return mainOutput.map((item: any) => item?.json || item || {});
  }

  get selectedNodeOutputColumns(): string[] {
    const items = this.selectedNodeOutputItems;
    if (items.length === 0) return [];
    const keys = new Set<string>();
    for (const item of items) {
      for (const key of Object.keys(item)) {
        keys.add(key);
      }
    }
    return Array.from(keys);
  }

  get selectedNodeItemCount(): number {
    return this.selectedLogsEntry?.itemCount || 0;
  }

  get selectedNodeSummary(): string {
    const entry = this.selectedLogsEntry;
    if (!entry) return '';
    return `${this.capitalizeStatus(entry.status)} in ${this.formatMs(entry.duration)}`;
  }

  get selectedNodeInputItems(): Record<string, any>[] {
    if (!this.selectedLogsNodeId || !this.executionData) return [];
    const raw = this.executionData[this.selectedLogsNodeId];
    if (!raw) return [];
    const run = Array.isArray(raw) ? raw[0] : raw;
    const mainInput = this.getRunMainInput(run);
    if (!Array.isArray(mainInput)) return [];
    return mainInput.map((item: any) => item?.json || item || {});
  }

  get selectedNodeInputColumns(): string[] {
    const items = this.selectedNodeInputItems;
    if (items.length === 0) return [];
    const keys = new Set<string>();
    for (const item of items) {
      for (const key of Object.keys(item)) keys.add(key);
    }
    return Array.from(keys);
  }

  get selectedNodeInputJson(): string {
    if (!this.selectedLogsNodeId || !this.executionData) return '';
    const raw = this.executionData[this.selectedLogsNodeId];
    if (!raw) return '';
    const run = Array.isArray(raw) ? raw[0] : raw;
    const mainInput = this.getRunMainInput(run);
    if (Array.isArray(mainInput)) {
      const items = mainInput.map((item: any) => item?.json || item || {});
      return JSON.stringify(items, null, 2);
    }
    return JSON.stringify(run?.inputData || {}, null, 2);
  }

  get activeDetailItems(): Record<string, any>[] {
    return this.detailView === 'input' ? this.selectedNodeInputItems : this.selectedNodeOutputItems;
  }

  get activeDetailColumns(): string[] {
    return this.detailView === 'input' ? this.selectedNodeInputColumns : this.selectedNodeOutputColumns;
  }

  get activeDetailJson(): string {
    return this.detailView === 'input' ? this.selectedNodeInputJson : this.selectedNodeOutputJson;
  }

  get activeDetailItemCount(): number {
    return this.activeDetailItems.length;
  }

  onLogsNodeClick(nodeId: string): void {
    this.selectedLogsNodeId = nodeId;
    this.detailView = 'output';
    this.displayMode = 'table';
    this.schemaSearch = '';
    this.searchExpanded = false;
    this.schemaCollapsed.clear();
  }

  toggleSearch(): void {
    this.searchExpanded = !this.searchExpanded;
    if (this.searchExpanded) {
      setTimeout(() => this.drawerSearchEl?.nativeElement.focus());
    } else {
      this.schemaSearch = '';
    }
  }

  onSearchBlur(): void {
    if (!this.schemaSearch) {
      this.searchExpanded = false;
    }
  }

  clearSearch(event: MouseEvent): void {
    event.preventDefault();
    this.schemaSearch = '';
    this.drawerSearchEl?.nativeElement.focus();
  }

  getNodeIconHtml(iconName: string): SafeHtml {
    if (this.iconCache.has(iconName)) return this.iconCache.get(iconName)!;

    const paths = ICON_PATHS[iconName];
    const svgContent = paths || '<rect x="3" y="3" width="18" height="18" rx="2"/>';
    const html = this.sanitizer.bypassSecurityTrustHtml(
      `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${svgContent}</svg>`
    );
    this.iconCache.set(iconName, html);
    return html;
  }

  capitalizeStatus(status: string): string {
    if (!status) return '';
    return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
  }

  formatMs(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const min = Math.floor(ms / 60000);
    const sec = Math.round((ms % 60000) / 1000);
    return `${min}m ${sec}s`;
  }

  formatCellValue(value: any): string {
    if (value === null || value === undefined) return '';
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    return JSON.stringify(value);
  }

  isArray(value: any): boolean {
    return Array.isArray(value);
  }

  isObjectValue(value: any): boolean {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  isEmptyObject(value: any): boolean {
    return value !== null && typeof value === 'object' && Object.keys(value).length === 0;
  }

  getObjectEntries(value: any): [string, any][] {
    if (!value || typeof value !== 'object') return [];
    return Object.entries(value);
  }

  // ── Schema view ──

  get activeDetailSchemaNodes(): SchemaNode[] {
    const items = this.activeDetailItems;
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.schemaCollapsed, this.schemaSearch);
  }

  buildSchema(items: Record<string, any>[], parentPath = '', level = 0): SchemaNode[] {
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
      const type = this.schemaTypeOf(value);
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

  toggleSchemaNode(path: string): void {
    if (this.schemaCollapsed.has(path)) {
      this.schemaCollapsed.delete(path);
    } else {
      this.schemaCollapsed.add(path);
    }
  }

  isSchemaCollapsed(path: string): boolean {
    return this.schemaCollapsed.has(path);
  }

  private schemaTypeOf(value: any): string {
    if (value === null || value === undefined) return 'null';
    if (Array.isArray(value)) return 'array';
    return typeof value;
  }

  typeIcon(type: string): string {
    switch (type) {
      case 'string': return 'Aa';
      case 'number': return '#';
      case 'boolean': return '\u2713';
      case 'object': return '{}';
      case 'array': return '[]';
      case 'null': return '\u2205';
      default: return '?';
    }
  }

  highlightText(text: string): SafeHtml {
    if (!this.schemaSearch || !text) return text;
    const html = this.escapeHtml(String(text));
    const escaped = this.schemaSearch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escaped})`, 'gi');
    const highlighted = html.replace(regex, '<mark class="search-hl">$1</mark>');
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  get activeDetailJsonHighlighted(): SafeHtml {
    const json = this.activeDetailJson;
    if (!json) return '';
    const html = this.escapeHtml(json);
    if (!this.schemaSearch) {
      return this.sanitizer.bypassSecurityTrustHtml(html);
    }
    const escaped = this.schemaSearch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escaped})`, 'gi');
    return this.sanitizer.bypassSecurityTrustHtml(html.replace(regex, '<mark class="search-hl">$1</mark>'));
  }

  private escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  get showClearExecution(): boolean {
    return !this.autoExpandOnView && this._viewingExecution;
  }

  onClearExecution(): void {
    if (this.expanded) {
      this.expanded = false;
      this.expandedChange.emit(false);
    }
    this.clearExecution.emit();
  }

  // ── Common ──

  toggleExpanded(): void {
    this.expanded = !this.expanded;
    this.expandedChange.emit(this.expanded);
  }

  selectTab(tab: 'output' | 'chat' | 'logs'): void {
    this.activeTab = tab;
    if (!this.expanded) {
      this.expanded = true;
      this.expandedChange.emit(true);
    }
    if (tab === 'chat') {
      this.connectChat();
      this.shouldScrollChat = true;
    }
  }

  onLogsResizeStart(event: MouseEvent): void {
    event.preventDefault();
    this.logsResizing = true;
    this.logsResizeStartX = event.clientX;
    this.logsResizeStartWidth = this.logsOverviewWidth;

    const onMove = (e: MouseEvent) => {
      if (!this.logsResizing) return;
      const delta = e.clientX - this.logsResizeStartX;
      const container = (event.target as HTMLElement).closest('.logs-content');
      const maxWidth = container ? container.clientWidth * 0.6 : 600;
      this.logsOverviewWidth = Math.max(180, Math.min(maxWidth, this.logsResizeStartWidth + delta));
    };

    const onUp = () => {
      this.logsResizing = false;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }

  onDragStart(event: MouseEvent): void {
    event.preventDefault();
    this.dragging = true;
    this.startY = event.clientY;
    this.startHeight = this.drawerHeight;

    const onMove = (e: MouseEvent) => {
      if (!this.dragging) return;
      const delta = this.startY - e.clientY;
      const parentHeight = (event.target as HTMLElement).closest('.editor-body')?.clientHeight || 800;
      const maxHeight = parentHeight * this.maxHeightPercent;
      this.drawerHeight = Math.max(this.minHeight, Math.min(maxHeight, this.startHeight + delta));
    };

    const onUp = () => {
      this.dragging = false;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }

  // Chat methods
  private connectChat(): void {
    if (!this.workflowId) return;
    // Create a session for this workflow's chat drawer
    this.chatService.createSession('Workflow Chat').subscribe({
      next: (session) => {
        this.chatSessionId = session.id;
        this.chatService.connect(session.id);
        this.chatService.messages$.subscribe(msg => {
          if (msg.sessionId === this.chatSessionId) {
            this.isTyping = false;
            this.messages.push(msg);
            this.shouldScrollChat = true;
          }
        });
      }
    });
  }

  sendMessage(): void {
    const content = this.chatInput.trim();
    if (!content || !this.chatSessionId) return;

    this.messages.push({ id: 'temp-' + Date.now(), sessionId: this.chatSessionId, role: 'user', content, createdAt: new Date().toISOString() });
    this.chatInput = '';
    this.isTyping = true;
    this.shouldScrollChat = true;

    this.chatService.sendMessage(this.chatSessionId, content).subscribe({
      error: () => {
        this.isTyping = false;
      }
    });
  }

  onChatKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }
}
