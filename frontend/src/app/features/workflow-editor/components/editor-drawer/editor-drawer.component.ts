import { Component, Input, Output, EventEmitter, ElementRef, ViewChild, AfterViewChecked, SimpleChanges, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ChatService, ChatMessage } from '../../../../core/services/chat.service';
import { WorkflowNode } from '../../../../core/models';

export interface LogsNodeEntry {
  node: WorkflowNode;
  displayName: string;
  iconName: string;
  status: string;
  duration: number;
  itemCount: number;
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

  @Input() set viewingExecution(value: boolean) {
    if (value && !this._viewingExecution) {
      this.activeTab = 'logs';
      this.selectedLogsNodeId = null;
      if (!this.expanded) {
        this.expanded = true;
        this.expandedChange.emit(true);
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

  selectedLogsNodeId: string | null = null;
  detailView: 'input' | 'output' = 'output';
  displayMode: 'table' | 'json' = 'table';
  @Output() openNodeDetail = new EventEmitter<string>();
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

  get logsNodeList(): LogsNodeEntry[] {
    if (!this.executionNodes?.length || !this.executionData) return [];

    const entries: LogsNodeEntry[] = [];

    for (const node of this.executionNodes) {
      const raw = this.executionData[node.id];
      if (!raw) continue;

      const typeDesc = this.nodeTypeMap?.get(node.type);
      const run = Array.isArray(raw) ? raw[0] : raw;
      if (!run || (run.startedAt == null && run.executionTime == null)) continue;
      const status = run?.error ? 'error' : (run?.status || 'success');
      const duration = run?.executionTime || 0;
      const outputItems = Array.isArray(raw)
        ? run?.data?.main?.[0]
        : (raw?.data?.main?.[0] || raw?.data);
      const itemCount = Array.isArray(outputItems) ? outputItems.length : 0;

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
      const aStart = (Array.isArray(aRaw) ? aRaw[0] : aRaw)?.startedAt || 0;
      const bStart = (Array.isArray(bRaw) ? bRaw[0] : bRaw)?.startedAt || 0;
      return aStart - bStart;
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
    const mainOutput = run?.data?.main?.[0];
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
    const mainOutput = run?.data?.main?.[0];
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
    const mainInput = run?.inputData?.main?.[0];
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
    const mainInput = run?.inputData?.main?.[0];
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
    if (this.messages.length === 0) {
      this.chatService.getHistory(this.workflowId).subscribe({
        next: (msgs) => {
          this.messages = msgs;
          this.shouldScrollChat = true;
        }
      });
    }
    this.chatService.connect(this.workflowId);
    this.chatService.messages$.subscribe(msg => {
      this.isTyping = false;
      this.messages.push(msg);
      this.shouldScrollChat = true;
    });
  }

  sendMessage(): void {
    const content = this.chatInput.trim();
    if (!content || !this.workflowId) return;

    this.messages.push({ role: 'user', content, timestamp: new Date() });
    this.chatInput = '';
    this.isTyping = true;
    this.shouldScrollChat = true;

    this.chatService.sendMessage(this.workflowId, content).subscribe({
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
