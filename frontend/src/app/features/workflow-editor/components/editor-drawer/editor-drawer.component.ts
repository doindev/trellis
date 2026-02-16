import { Component, Input, Output, EventEmitter, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatMessage } from '../../../../core/services/chat.service';

@Component({
  selector: 'app-editor-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './editor-drawer.component.html',
  styleUrl: './editor-drawer.component.scss'
})
export class EditorDrawerComponent implements AfterViewChecked {
  @Input() executionData: Record<string, any> | null = null;
  @Input() selectedNodeId: string | null = null;
  @Input() isExecuting = false;
  @Input() workflowId: string | null = null;

  @Input() expanded = false;
  @Output() expandedChange = new EventEmitter<boolean>();

  @ViewChild('chatMessages') chatMessagesEl?: ElementRef<HTMLDivElement>;

  activeTab: 'output' | 'chat' = 'output';
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

  constructor(private chatService: ChatService) {}

  ngAfterViewChecked(): void {
    if (this.shouldScrollChat && this.chatMessagesEl) {
      const el = this.chatMessagesEl.nativeElement;
      el.scrollTop = el.scrollHeight;
      this.shouldScrollChat = false;
    }
  }

  get nodeExecutionData(): any {
    if (!this.executionData || !this.selectedNodeId) return null;
    return this.executionData[this.selectedNodeId];
  }

  get outputJson(): string {
    const data = this.nodeExecutionData;
    if (!data) return '';
    return JSON.stringify(data.data || data, null, 2);
  }

  get executionStatus(): string {
    return this.nodeExecutionData?.status || (this.isExecuting ? 'running' : 'idle');
  }

  toggleExpanded(): void {
    this.expanded = !this.expanded;
    this.expandedChange.emit(this.expanded);
  }

  selectTab(tab: 'output' | 'chat'): void {
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
      const parentHeight = (event.target as HTMLElement).closest('.canvas-and-drawer')?.clientHeight || 800;
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
