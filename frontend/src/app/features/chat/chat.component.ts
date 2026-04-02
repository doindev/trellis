import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatService } from '../../core/services/chat.service';
import { WorkflowService } from '../../core/services/workflow.service';
import { ChatMessage } from '../../core/models/chat.model';
import { Workflow } from '../../core/models/workflow.model';

@Component({
    selector: 'app-chat',
    imports: [CommonModule, FormsModule],
    templateUrl: './chat.component.html',
    styleUrl: './chat.component.scss'
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  view: string = '';
  sessionId: string = '';
  messages: ChatMessage[] = [];
  messageInput = '';
  sending = false;
  loading = false;

  // Predefined agents (workflows with type=AGENT)
  agents: Workflow[] = [];
  loadingAgents = false;

  @ViewChild('messagesContainer') messagesContainer!: ElementRef;
  @ViewChild('messageInputEl') messageInputEl!: ElementRef;
  private shouldScrollToBottom = false;

  private routeSub?: Subscription;
  private wsSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private chatService: ChatService,
    private workflowService: WorkflowService
  ) {}

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const viewParam = params.get('view');
      if (viewParam === 'personal-agents' || viewParam === 'agents') {
        this.view = 'agents';
        this.sessionId = '';
        this.chatService.disconnect();
        this.loadAgents();
      } else if (viewParam === 'workflow-agents') {
        this.view = 'agents';
        this.sessionId = '';
        this.chatService.disconnect();
        this.loadAgents();
      } else if (viewParam) {
        // It's a session ID
        this.view = 'conversation';
        this.sessionId = viewParam;
        this.loadMessages();
        this.connectWebSocket();
      } else {
        this.view = 'empty';
        this.sessionId = '';
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    this.wsSub?.unsubscribe();
    this.chatService.disconnect();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  loadMessages(): void {
    this.loading = true;
    this.chatService.getMessages(this.sessionId).subscribe({
      next: (msgs) => {
        this.messages = msgs;
        this.loading = false;
        this.shouldScrollToBottom = true;
      },
      error: () => this.loading = false
    });
  }

  connectWebSocket(): void {
    this.wsSub?.unsubscribe();
    this.chatService.connect(this.sessionId);
    this.wsSub = this.chatService.messages$.subscribe(msg => {
      if (msg.sessionId === this.sessionId) {
        if (msg.role === 'status') {
          const lastIdx = this.messages.length - 1;
          if (lastIdx >= 0 && this.messages[lastIdx].role === 'status') {
            this.messages[lastIdx] = msg;
          } else {
            this.messages.push(msg);
          }
        } else {
          if (this.messages.length > 0 && this.messages[this.messages.length - 1].role === 'status') {
            this.messages.pop();
          }
          this.messages.push(msg);
          if (msg.role === 'assistant') {
            this.sending = false;
          }
        }
        this.shouldScrollToBottom = true;
      }
    });
  }

  onSend(): void {
    const content = this.messageInput.trim();
    if (!content || this.sending) return;

    this.sending = true;
    const userMsg: ChatMessage = {
      id: 'temp-' + Date.now(),
      sessionId: this.sessionId,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    };
    this.messages.push(userMsg);
    this.messageInput = '';
    this.shouldScrollToBottom = true;

    this.chatService.sendMessage(this.sessionId, content).subscribe({
      next: (saved) => {
        // Replace temp message with saved one
        const idx = this.messages.findIndex(m => m.id === userMsg.id);
        if (idx >= 0) this.messages[idx] = saved;
        // sending stays true until the AI response arrives via WebSocket
      },
      error: () => this.sending = false
    });
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSend();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.sending && this.view === 'conversation') {
      this.interruptChat();
    }
  }

  interruptChat(): void {
    this.sending = false;
    this.chatService.interruptChat(this.sessionId).subscribe();

    // Remove any trailing status messages
    while (this.messages.length > 0 && this.messages[this.messages.length - 1].role === 'status') {
      this.messages.pop();
    }

    // Restore the last user message back to the input
    if (this.messages.length > 0 && this.messages[this.messages.length - 1].role === 'user') {
      const lastUserMsg = this.messages.pop()!;
      this.messageInput = lastUserMsg.content;
    }

    // Focus the input so the user can immediately type
    setTimeout(() => this.messageInputEl?.nativeElement?.focus(), 0);
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch (e) {}
  }

  // Markdown rendering (simple regex-based)
  renderMarkdown(text: string): string {
    if (!text) return '';
    let html = this.escapeHtml(text);
    // Code blocks
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>');
    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Bold
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    // Italic
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
    // Links
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
    // Unordered lists
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');
    // Ordered lists
    html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');
    // Newlines (but not inside pre blocks)
    html = html.replace(/(?<!\<\/pre\>)\n/g, '<br>');
    return html;
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  // ── Predefined Agents ──

  loadAgents(): void {
    this.loadingAgents = true;
    this.workflowService.list({ type: 'AGENT' }).subscribe({
      next: (agents) => {
        this.agents = agents;
        this.loadingAgents = false;
      },
      error: () => this.loadingAgents = false
    });
  }

  onChatWithAgent(agent: Workflow): void {
    this.chatService.createSession(agent.name, agent.id).subscribe({
      next: (session) => {
        this.router.navigate(['/home/chat', session.id]);
      }
    });
  }

  onCreateAgent(): void {
    this.router.navigate(['/agent/new']);
  }

  onOpenAgent(agent: Workflow): void {
    if (agent.id) {
      this.router.navigate(['/agent', agent.id]);
    }
  }
}
